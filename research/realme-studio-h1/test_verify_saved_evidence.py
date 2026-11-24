"""Offline provenance checks; synthetic fixtures do not establish hardware support."""

import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from rcsp_status import FrameParser, STATUS_REQUEST, parse_status_payload
from test_rcsp_status import UPSTREAM_STATUS_RESPONSE
import verify_saved_evidence as verify


def synthetic_capture():
    response = bytearray(UPSTREAM_STATUS_RESPONSE)
    response[8] = 1
    status = parse_status_payload(FrameParser().feed(response)[0].payload)
    capture = {
        "outbound_request_hex": STATUS_REQUEST.hex(),
        "received_chunks": [{"hex": response.hex()}],
        "actual_vendor_send_calls": 1,
        "actual_vendor_bytes_sent": 14,
    }
    observation = {"reported_status": status, "received_bytes": len(response)}
    return capture, observation


class EvidenceVerificationTests(unittest.TestCase):
    def test_exact_capture_digest_and_interpretation(self):
        capture, observation = synthetic_capture()
        payload = (json.dumps(capture, indent=2) + "\n").encode()
        observation["private_capture_sha256"] = hashlib.sha256(payload).hexdigest()
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "capture.json"
            path.write_bytes(payload)
            result = verify.verify_capture(path, observation)
            self.assertEqual(result["original_hash_scope"], "exact_file_bytes")
            self.assertTrue(result["public_status_matches_private_bytes"])
            self.assertEqual(path.read_bytes(), payload)

    def test_windows_newline_scope_is_explicit_without_rewriting(self):
        capture, observation = synthetic_capture()
        payload = (json.dumps(capture, indent=2) + "\n").encode()
        observation["private_capture_sha256"] = hashlib.sha256(payload).hexdigest()
        windows_bytes = payload.replace(b"\n", b"\r\n")
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "capture.json"
            path.write_bytes(windows_bytes)
            result = verify.verify_capture(path, observation)
            self.assertEqual(result["original_hash_scope"], "LF-normalized_UTF-8_JSON_payload")
            self.assertEqual(result["actual_capture_file_sha256"], hashlib.sha256(windows_bytes).hexdigest())
            self.assertEqual(path.read_bytes(), windows_bytes)

    def test_status_mismatch_is_rejected(self):
        capture, observation = synthetic_capture()
        payload = json.dumps(capture).encode()
        observation["private_capture_sha256"] = hashlib.sha256(payload).hexdigest()
        observation["reported_status"]["battery_percent"] = 99
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "capture.json"
            path.write_bytes(payload)
            with self.assertRaises(ValueError):
                verify.verify_capture(path, observation)

    def test_modified_capture_digest_is_rejected(self):
        capture, observation = synthetic_capture()
        observation["private_capture_sha256"] = "0" * 64
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "capture.json"
            path.write_text(json.dumps(capture), encoding="utf-8")
            with self.assertRaises(ValueError):
                verify.verify_capture(path, observation)

    def test_retained_source_identity_is_checked(self):
        payload = b"synthetic public source"
        source = {"source_commit": "test", "source_files": [
            {"path": "source.kt", "bytes": len(payload), "sha256": hashlib.sha256(payload).hexdigest()}
        ]}
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "source.kt"
            path.write_bytes(payload)
            self.assertEqual(verify.verify_source_files(Path(directory), source)["verified_source_files"], ["source.kt"])
            path.write_bytes(b"changed")
            with self.assertRaises(ValueError):
                verify.verify_source_files(Path(directory), source)

    def test_saved_owner_observation_retains_safety_boundary(self):
        observation = json.loads((verify.DIRECTORY / "windows-status-observation.json").read_text(encoding="utf-8"))
        verification = json.loads((verify.DIRECTORY / "windows-status-verification.json").read_text(encoding="utf-8"))
        self.assertEqual(observation["safety"]["vendor_query_send_calls"], 1)
        self.assertEqual(observation["safety"]["vendor_bytes_sent"], 14)
        self.assertFalse(observation["safety"]["setting_change_commands_sent"])
        self.assertTrue(observation["safety"]["socket_closed"])
        self.assertEqual(verification["status_capture"]["reported_battery_percent"], 39)
        self.assertTrue(verification["status_capture"]["public_status_matches_private_bytes"])


if __name__ == "__main__":
    unittest.main()
