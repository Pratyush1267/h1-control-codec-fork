"""Offline safety and transport-shape tests; no Bluetooth APIs are invoked."""

import contextlib
import ctypes
import hashlib
import io
import json
import tempfile
import unittest
import uuid
from pathlib import Path
from unittest.mock import patch

import h1_status_probe as probe
from rcsp_status import STATUS_REQUEST
from test_rcsp_status import UPSTREAM_STATUS_RESPONSE


class FakeTransport:
    def __init__(self, chunks):
        self.chunks = iter(chunks)
        self.sent = []

    def send_status_once(self, deadline):
        self.sent.append(STATUS_REQUEST)
        return len(STATUS_REQUEST)

    def read_once(self, deadline, limit):
        try:
            return next(self.chunks)
        except StopIteration:
            raise probe.ProbeError("wait", "Synthetic timeout")


class StatusProbeSafetyTests(unittest.TestCase):
    def test_windows_packed_address_uses_uuid_not_guessed_channel(self):
        address = probe.service_address(0x123456789ABC)
        self.assertEqual(ctypes.sizeof(address), 30)
        self.assertEqual(probe.SOCKADDR_BTH.btAddr.offset, 2)
        self.assertEqual(probe.SOCKADDR_BTH.serviceClassId.offset, 10)
        self.assertEqual(probe.SOCKADDR_BTH.port.offset, 26)
        self.assertEqual(address.port, 0)
        self.assertEqual(str(uuid.UUID(bytes_le=bytes(address.serviceClassId))), probe.SPP_UUID)

    def test_fd_set_and_timeval_shapes(self):
        self.assertEqual(probe.FD_SET.fd_array.offset, ctypes.sizeof(probe.SOCKET))
        self.assertEqual(ctypes.sizeof(probe.TIMEVAL), 8)

    def test_default_is_offline(self):
        stdout = io.StringIO()
        with patch.object(probe, "run_probe", side_effect=AssertionError("must not run")), contextlib.redirect_stdout(stdout):
            self.assertEqual(probe.main([]), 0)
        result = json.loads(stdout.getvalue())
        self.assertFalse(result["socket_opened"])
        self.assertEqual(result["vendor_bytes_sent"], 0)

    def test_execution_requires_evidence_paths_before_running(self):
        with patch.object(probe, "run_probe", side_effect=AssertionError("must not run")), contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit):
                probe.main(["--execute-status-query"])

    def test_existing_output_prevents_any_query(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "existing.json"
            output.write_text("user data", encoding="utf-8")
            with patch.object(probe, "run_probe", side_effect=AssertionError("must not run")), contextlib.redirect_stderr(io.StringIO()):
                result = probe.main(["--execute-status-query", "--output", str(output), "--private-capture", str(Path(directory) / "raw.json")])
            self.assertEqual(result, 1)
            self.assertEqual(output.read_text(encoding="utf-8"), "user data")

    def test_written_private_hash_matches_exact_file_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            public = Path(directory) / "observation.json"
            private = Path(directory) / "private.json"
            observation = probe.new_observation()
            observation["outcome"] = "status_response_verified"
            capture = {"synthetic": True, "received_chunks": []}
            with patch.object(probe, "run_probe", return_value=(observation, capture)), contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(probe.main(["--execute-status-query", "--output", str(public), "--private-capture", str(private)]), 0)
            saved = json.loads(public.read_text(encoding="utf-8"))
            self.assertEqual(saved["private_capture_sha256"], hashlib.sha256(private.read_bytes()).hexdigest())

    def test_existing_private_file_prevents_any_query(self):
        with tempfile.TemporaryDirectory() as directory:
            private = Path(directory) / "private.json"
            private.write_text("keep", encoding="utf-8")
            with patch.object(probe, "run_probe", side_effect=AssertionError("must not run")), contextlib.redirect_stderr(io.StringIO()):
                result = probe.main(["--execute-status-query", "--output", str(Path(directory) / "new.json"), "--private-capture", str(private)])
            self.assertEqual(result, 1)
            self.assertEqual(private.read_text(encoding="utf-8"), "keep")

    def test_single_request_with_split_response(self):
        # Synthetic sequence rewrite of an upstream fixture; not hardware evidence.
        response = bytearray(UPSTREAM_STATUS_RESPONSE)
        response[8] = 1
        transport = FakeTransport([bytes(response[:9]), bytes(response[9:])])
        observation = probe.new_observation()
        capture = {"received_chunks": []}
        probe.exchange_status_once(transport, observation, capture)
        self.assertEqual(transport.sent, [STATUS_REQUEST])
        self.assertEqual(observation["outcome"], "status_response_verified")
        self.assertEqual(observation["reported_status"]["battery_percent"], 38)

    def test_incoming_command_does_not_generate_ack_or_retry(self):
        transport = FakeTransport([bytes.fromhex("FE DC BA C0 09 00 02 07 FF EF")])
        observation = probe.new_observation()
        with self.assertRaises(probe.ProbeError):
            probe.exchange_status_once(transport, observation, {"received_chunks": []})
        self.assertEqual(transport.sent, [STATUS_REQUEST])
        self.assertFalse(observation["matching_status_response_received"])

    def test_unmatched_sequence_does_not_establish_success(self):
        transport = FakeTransport([UPSTREAM_STATUS_RESPONSE])
        observation = probe.new_observation()
        with self.assertRaises(probe.ProbeError):
            probe.exchange_status_once(transport, observation, {"received_chunks": []})
        self.assertFalse(observation["matching_status_response_received"])
        self.assertEqual(transport.sent, [STATUS_REQUEST])

    def test_transport_has_no_second_send_path(self):
        transport = probe.QueryOnlyRfcomm(0)
        transport.connected = True
        transport.send_calls = 1
        with self.assertRaises(probe.ProbeError):
            transport.send_status_once(0)

    def test_public_observation_omits_device_address(self):
        result = probe.new_observation()
        self.assertEqual(result["device_address"], "redacted")
        self.assertFalse(result["safety"]["setting_change_commands_sent"])
        self.assertIsNone(result["reported_status"])


if __name__ == "__main__":
    unittest.main()
