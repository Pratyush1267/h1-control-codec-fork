"""Offline checks for the RFCOMM link scaffold's safety boundaries."""

import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EVIDENCE = ROOT / "research" / "realme-studio-h1"
SOURCE = (
    ROOT
    / "android-app"
    / "app"
    / "src"
    / "main"
    / "java"
    / "com"
    / "example"
    / "h1companion"
    / "h1link"
    / "H1Link.kt"
)


class H1LinkContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = SOURCE.read_text(encoding="utf-8")
        cls.evidence = json.loads((EVIDENCE / "h1-control-evidence.json").read_text(encoding="utf-8"))

    def test_spp_uuid_matches_pinned_upstream(self):
        expected = self.evidence["protocol_source"]["service_uuid"]
        self.assertIn(expected, self.source)

    def test_writes_are_disabled_by_default(self):
        self.assertIn("private val allowWrites: Boolean = false", self.source)
        self.assertIn("if (!allowWrites)", self.source)
        self.assertIn("Headset setters are disabled in this build.", self.source)

    def test_no_polling_or_automatic_reconnect(self):
        for forbidden in ["delay(", "scheduleReconnect", "RECONNECT_DELAYS", "BATTERY_POLL_MS"]:
            self.assertNotIn(forbidden, self.source)
        self.assertIn("automatic reconnect", self.source)

    def test_status_is_requested_once_per_explicit_refresh(self):
        self.assertEqual(1, self.source.count("requestStatus()"))
        self.assertIn("fun refresh()", self.source)

    def test_parsing_stays_single_threaded_and_permission_is_checked(self):
        self.assertIn("commands.execute { session?.onBytes(chunk) }", self.source)
        self.assertIn("context.checkSelfPermission", self.source)
        self.assertIn("BLUETOOTH_CONNECT", self.source)


if __name__ == "__main__":
    unittest.main()
