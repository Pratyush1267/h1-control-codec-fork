"""Check the recorded APK identity against the private build receipt."""

import hashlib
import json
import os
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EVIDENCE = ROOT / "research" / "realme-studio-h1"
TEMP = Path(os.environ["LOCALAPPDATA"]) / "Temp" / "opencode"
BUILD = TEMP / ("h1-lean-build-" + os.environ["OPENCODE_SESSION_ID"]) / "build"


class AndroidBuildTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.evidence = json.loads((EVIDENCE / "android-build-evidence.json").read_text(encoding="utf-8"))
        cls.receipt = json.loads((BUILD / "receipt.json").read_text(encoding="utf-8"))

    def test_artifact_hash_matches_receipt(self):
        artifact = self.evidence["artifact"]
        self.assertEqual(artifact["sha256"], self.receipt["artifact"]["sha256"])
        self.assertEqual(artifact["bytes"], self.receipt["artifact"]["bytes"])
        apk = BUILD / "h1companion-debug.apk"
        self.assertEqual(artifact["sha256"], hashlib.sha256(apk.read_bytes()).hexdigest())

    def test_signer_record_matches_verification_output(self):
        verification = (BUILD / "apksigner-verification.txt").read_text(encoding="utf-8")
        self.assertIn("Verifies", verification)
        self.assertIn(self.evidence["signing"]["signer_certificate_sha256"], verification)

    def test_build_steps_are_the_declared_lean_pipeline(self):
        names = [step["name"] for step in self.receipt["steps"]]
        # keytool is skipped on rebuilds when the local debug keystore already exists.
        without_keytool = [name for name in names if name != "keytool"]
        self.assertEqual(["kotlinc", "d8", "aapt2-link", "apksign", "apksigner-verify"], without_keytool)
        self.assertTrue(all(step["exit_code"] == 0 for step in self.receipt["steps"]))

    def test_install_observation_is_recorded(self):
        install = json.loads((EVIDENCE / "android-install-observation.json").read_text(encoding="utf-8"))
        self.assertEqual("Success", install["install_result"])
        self.assertEqual(self.evidence["artifact"]["sha256"], install["apk_sha256"])
        self.assertEqual("com.example.h1companion", install["package"])


if __name__ == "__main__":
    unittest.main()
