"""Verify the H1 Control fork evidence matches the pinned source and built APK."""

import hashlib
import json
import os
import unittest
from pathlib import Path

TEMP = Path(os.environ["LOCALAPPDATA"]) / "Temp" / "opencode"
EVIDENCE = Path(__file__).resolve().parent / "h1-control-fork-evidence.json"
SOURCE = TEMP / ("h1-control-full-" + os.environ["OPENCODE_SESSION_ID"]) / "repo"
ARCHIVE = TEMP / ("h1-control-full-" + os.environ["OPENCODE_SESSION_ID"]) / "source.zip"
APK = SOURCE / "androidApp" / "build" / "outputs" / "apk" / "debug" / "androidApp-debug.apk"


class H1ControlForkTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = json.loads(EVIDENCE.read_text(encoding="utf-8"))

    def test_source_archive_hash(self):
        expected = self.data["upstream"]["source_archive_sha256"]
        self.assertEqual(expected, hashlib.sha256(ARCHIVE.read_bytes()).hexdigest())

    def test_built_apk_hash(self):
        expected = self.data["build"]["apk_sha256"]
        self.assertEqual(expected, hashlib.sha256(APK.read_bytes()).hexdigest())

    def test_fork_files_contain_the_added_route(self):
        quality = (SOURCE / "androidApp/src/main/kotlin/dev/madhuraj/h1/CodecQuality.kt").read_text(encoding="utf-8")
        self.assertIn('getMethod(\n            "setCodecConfigPreference"', quality)
        self.assertIn("1_000_000", quality)
        self.assertIn("setSelfManaged(false)", (SOURCE / "androidApp/src/main/kotlin/dev/madhuraj/h1/CompanionAssociation.kt").read_text(encoding="utf-8"))
        monitor = (SOURCE / "androidApp/src/main/kotlin/dev/madhuraj/h1/CodecMonitor.kt").read_text(encoding="utf-8")
        self.assertIn("fun setQuality(quality: LdacQuality)", monitor)
        screen = (SOURCE / "androidApp/src/main/kotlin/dev/madhuraj/h1/ui/MainScreen.kt").read_text(encoding="utf-8")
        self.assertIn("LDAC playback quality", screen)

    def test_side_by_side_application_id(self):
        gradle = (SOURCE / "androidApp/build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn('applicationId = "dev.madhuraj.h1.fork"', gradle)


if __name__ == "__main__":
    unittest.main()
