"""Offline checks that the Android scaffold matches the validated phone evidence."""

import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EVIDENCE = ROOT / "research" / "realme-studio-h1"
APP = ROOT / "android-app" / "app" / "src" / "main"


def read(relative):
    return (APP / relative).read_text(encoding="utf-8")


class CodecContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.installed = json.loads(
            (EVIDENCE / "installed-261-static-evidence.json").read_text(encoding="utf-8")
        )
        cls.static = json.loads(
            (EVIDENCE / "codec-changer-static-evidence.json").read_text(encoding="utf-8")
        )
        cls.quality = read("java/com/example/h1companion/codec/LdacQuality.kt")
        cls.factory = read("java/com/example/h1companion/codec/CodecConfigFactory.kt")
        cls.controller = read("java/com/example/h1companion/codec/CodecController.kt")
        cls.onboarding = read("java/com/example/h1companion/codec/CompanionOnboarding.kt")
        cls.policy = read("java/com/example/h1companion/codec/AssociationPolicy.kt")
        cls.manifest = read("AndroidManifest.xml")

    def test_quality_values_match_installed_constants(self):
        # Values come from CodecConstants in the installed 2.6.1 build and the
        # observed active policy recorded in codec-quality-roundtrip.json.
        source = self.quality
        for name, value in [
            ("OPTIMIZED_AUDIO", 1000),
            ("BALANCED", 1001),
            ("OPTIMIZED_CONNECTION", 1002),
            ("BEST_EFFORT", 1003),
        ]:
            match = re.search(rf"{name}\({value},", source)
            self.assertIsNotNone(match, f"{name} must use the observed value {value}")

    def test_constructor_order_matches_decompiled_factory(self):
        factory_source = self.factory
        block = re.search(r"constructor\.newInstance\((.*?)\)\s+as BluetoothCodecConfig", factory_source, re.S)
        self.assertIsNotNone(block)
        arguments = [item.strip() for item in block.group(1).split(",") if item.strip()]
        self.assertEqual(
            ["CODEC_TYPE_LDAC", "SELECTED_CODEC_PRIORITY", "sampleRate", "bitsPerSample",
             "channelMode", "quality.codecSpecific1", "0L", "0L", "0L"],
            arguments,
        )
        normalized = re.sub(r"\s\*\s", " ", " ".join(factory_source.split()))
        self.assertIn(
            "createLegacy(codecType, 1000000, sampleRate, bitsPerSample, channelMode, "
            "codecSpecific1, 0, 0, 0)",
            normalized,
        )

    def test_manifest_has_no_privileged_permissions(self):
        declared = "\n".join(
            line for line in self.manifest.splitlines() if "<uses-permission" in line
        )
        for denied in ["BLUETOOTH_PRIVILEGED", "WRITE_SECURE_SETTINGS", "DUMP", "READ_LOGS"]:
            self.assertNotIn(denied, declared)
        self.assertIn("android.permission.BLUETOOTH_CONNECT", declared)

    def test_controller_uses_getmethod_without_setaccessible_on_a2dp_methods(self):
        self.assertEqual(1, self.controller.count('getMethod("getCodecStatus"'))
        self.assertEqual(2, self.controller.count("getMethod("))
        self.assertNotIn("a2dpMethod.isAccessible", self.controller)
        self.assertIn("constructor.isAccessible = true", self.factory)

    def test_error_states_remain_explicit(self):
        for name in ["MethodUnavailable", "PermissionDenied", "InvalidConfig", "Unavailable"]:
            self.assertIn(f"class {name}", self.controller)
        self.assertIn("OBSERVED", read("java/com/example/h1companion/codec/CodecUiState.kt"))
        self.assertIn("PENDING", read("java/com/example/h1companion/codec/CodecUiState.kt"))

    def test_manifest_declares_companion_feature_without_self_managed_permission(self):
        self.assertIn("android.software.companion_device_setup", self.manifest)
        self.assertNotIn("REQUEST_COMPANION_SELF_MANAGED", self.manifest)

    def test_onboarding_uses_system_consent_and_rejects_self_managed(self):
        source = self.onboarding
        for token in [
            "addDeviceFilter",
            "BluetoothDeviceFilter.Builder().setAddress(device.address)",
            ".setSingleDevice(true)",
            ".setSelfManaged(false)",
            "manager.associate(request, executor",
            "onAssociationPending",
            "startIntentSenderForResult(intentSender",
            "onAssociationCreated",
            "EXTRA_ASSOCIATION",
        ]:
            self.assertIn(token, source, f"missing onboarding token: {token}")
        self.assertNotIn(".setSelfManaged(true)", source)
        self.assertNotIn("REQUEST_COMPANION_SELF_MANAGED", source)

    def test_controller_gate_runs_before_reflection(self):
        source = self.controller
        for gate in ["if (!readGate(device))", "if (!writeGate(device))"]:
            self.assertIn(gate, source)
        self.assertIn("class AssociationMissing", source)
        self.assertLess(
            source.index("if (!readGate(device))"),
            source.index("method.invoke(a2dp, device)"),
        )
        self.assertLess(
            source.index("if (!writeGate(device))"),
            source.index("method.invoke(a2dp, device, config)"),
        )

    def test_association_policy_matches_versioned_aosp_snapshots(self):
        self.assertIn("sdkInt >= 34", self.policy)
        self.assertIn("sdkInt >= 35", self.policy)
        self.assertIn("SELF_MANAGED_NOT_ACCEPTED", self.policy)


if __name__ == "__main__":
    unittest.main()
