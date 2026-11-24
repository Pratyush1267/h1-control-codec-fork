"""Offline safety/serialization tests. These tests do not call Bluetooth APIs."""

import ctypes
import json
import tempfile
import unittest
import uuid
from pathlib import Path

import windows_probe as probe


class CacheProbeTests(unittest.TestCase):
    def test_no_inquiry_or_unknown_device_search(self):
        params = probe.cache_only_search_params()
        self.assertEqual(params.fIssueInquiry, 0)
        self.assertEqual(params.fReturnUnknown, 0)
        self.assertEqual(params.cTimeoutMultiplier, 0)
        self.assertEqual(params.dwSize, ctypes.sizeof(params))

    def test_native_guid_roundtrip(self):
        value = uuid.UUID("df21fe2c-2515-4fdb-8886-f12c4d67927c")
        native = probe.GUID.from_buffer_copy(value.bytes_le)
        self.assertEqual(uuid.UUID(bytes_le=bytes(native)), value)
        self.assertEqual(ctypes.sizeof(native), 16)

    def test_standard_service_is_not_assumed_control(self):
        value = probe.service_record("00001101-0000-1000-8000-00805F9B34FB")
        self.assertEqual(value["recognized_name"], "Serial Port (SPP)")
        self.assertFalse(value["headset_control_verified"])

    def test_unknown_service_remains_unclassified(self):
        value = probe.service_record("12345678-1234-5678-9abc-123456789abc")
        self.assertIsNone(value["recognized_name"])
        self.assertFalse(value["headset_control_verified"])

    def test_observation_omits_address_and_retains_unknowns(self):
        info = probe.BLUETOOTH_DEVICE_INFO()
        info.szName = probe.DEFAULT_NAME
        info.Address.ullLong = 0x123456789ABC
        info.fConnected = 1
        result = probe.build_observation(info, [])
        self.assertTrue(result["device"]["windows_reports_connected"])
        self.assertEqual(result["device"]["address"], "redacted")
        self.assertNotIn("123456789", json.dumps(result))
        self.assertIsNone(result["device"]["firmware_version"])
        self.assertFalse(result["device"]["physical_model_label_verified"])
        self.assertFalse(any(result["safety"].values()))

    def test_output_refuses_overwrite(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "observation.json"
            probe.save_new_json(path, {"original": True})
            with self.assertRaises(FileExistsError):
                probe.save_new_json(path, {"original": False})
            self.assertEqual(json.loads(path.read_text(encoding="utf-8")), {"original": True})


if __name__ == "__main__":
    unittest.main()
