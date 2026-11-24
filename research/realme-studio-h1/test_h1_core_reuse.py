"""Verify the adapted Kotlin core came from the pinned MIT source and stayed honest."""

import hashlib
import json
import os
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EVIDENCE = ROOT / "research" / "realme-studio-h1"
TEMP = Path(os.environ["LOCALAPPDATA"]) / "Temp" / "opencode"
PINNED = TEMP / ("realme-h1-protocol-source-" + os.environ["OPENCODE_SESSION_ID"])
ADAPTED = ROOT / "android-app" / "app" / "src" / "main" / "java" / "com" / "example" / "h1companion" / "h1core"
TESTS = ROOT / "android-app" / "app" / "src" / "test" / "java" / "com" / "example" / "h1companion" / "h1core"


class H1CoreReuseTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.evidence = json.loads((EVIDENCE / "h1-control-evidence.json").read_text(encoding="utf-8"))
        cls.hashes = {entry["path"]: entry["sha256"] for entry in cls.evidence["source_files"]}

    def test_pinned_upstream_hashes_are_unchanged(self):
        for path, expected in self.hashes.items():
            actual = hashlib.sha256((PINNED / path).read_bytes()).hexdigest()
            self.assertEqual(expected, actual, f"pinned upstream file changed: {path}")

    def test_adapted_files_carry_mit_attribution(self):
        files = [
            ADAPTED / "Rcsp.kt",
            ADAPTED / "FrameParser.kt",
            ADAPTED / "HeadsetSession.kt",
            TESTS / "HeadsetSessionTest.kt",
        ]
        for path in files:
            text = path.read_text(encoding="utf-8")
            self.assertIn("a7d08b045164996c7b5153660eb7d640898b3242", text, path)
            self.assertIn("Madhuraj", text, path)
            self.assertIn("package com.example.h1companion.h1core", text, path)

    def test_set_path_is_not_optimistic(self):
        text = (ADAPTED / "HeadsetSession.kt").read_text(encoding="utf-8")
        self.assertIn("pendingNoiseMode", text)
        self.assertIn("update(state.copy(pendingNoiseMode = mode))", text)
        self.assertNotIn("update(state.copy(noiseMode = mode))", text)

    def test_pending_semantics_are_tested(self):
        text = (TESTS / "HeadsetSessionTest.kt").read_text(encoding="utf-8")
        self.assertIn("requested mode stays pending until the headset reports it", text)
        self.assertIn("assertEquals(NoiseMode.OFF, s.state.noiseMode)", text)


if __name__ == "__main__":
    unittest.main()
