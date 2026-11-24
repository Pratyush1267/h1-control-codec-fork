# H1 companion app — codec adapter scaffold

Status: **compiled, signed, installed and launched on the owner's phone.** The
portable toolchain built `h1companion-debug.apk`; `apksigner verify` passed; ADB
install succeeded and `MainActivity` launched without a crash. It is still a manual
test surface, not a complete app, and exposes no headset setters.
See [android-build-evidence.json](../research/realme-studio-h1/android-build-evidence.json)
and [android-install-observation.json](../research/realme-studio-h1/android-install-observation.json).

## What is implemented

- `LdacQuality`: the four LDAC policy values and labels observed in the installed
  Bluetooth Codec Changer 2.6.1 build.
- `CodecConfigFactory`: builds `BluetoothCodecConfig` through the hidden 9-argument
  constructor in the platform order confirmed from both the decompiled app and the
  AOSP service rejection seen during testing.
- `A2dpCodecController`: resolves and invokes `getCodecStatus` /
  `setCodecConfigPreference` by reflection, with explicit error states. It does not
  call `setAccessible` on the A2DP methods and requests no privileged permission.
- `CodecStatusReader`: reads the active codec and quality from a codec status object
  through the same compat-reflection approach, keeping current, supported and
  requested values separate.
- `CodecUiState`: observed/requested separation with source and freshness markers.
- `AssociationPolicy` / `AssociationEvaluator`: pure association decisions and SDK
  version qualification, with JVM unit tests.
- `CompanionOnboarding`: user-approved CDM association flow using the documented
  `associate(request, executor, callback)` API and system consent UI; it explicitly
  rejects self-managed associations and supplies read/write gates to the controller.
- `h1core/`: MIT-attributed, adapted copies of the pinned H1 Control RCSP framing,
  stream parser and Bluetooth-free session state machine. The set-path was changed
  to keep a requested noise mode pending until the headset reports it; no Android
  RFCOMM transport was copied. See
  [../research/realme-studio-h1/THIRD-PARTY-NOTICES.md](../research/realme-studio-h1/THIRD-PARTY-NOTICES.md).
- `h1link/H1Link.kt`: independent, read-only-by-default RFCOMM link using one
  command executor and one reader executor, no polling, no automatic reconnect,
  one status request per explicit `refresh()`, and setters disabled unless
  `allowWrites` is explicitly enabled. A bounded ADB-shell probe could not open
  either documented socket route, so this link is untested on hardware; see
  [android-rfcomm-observation.json](../research/realme-studio-h1/android-rfcomm-observation.json).

Design note: [COMPANION-ONBOARDING.md](../research/realme-studio-h1/COMPANION-ONBOARDING.md).

## What is deliberately not implemented

- No UI, and no association has been created by this scaffold on the owner's phone.
  The observed association belongs to the commercial app; onboarding is source-only
  here and must be run and validated before this adapter can rely on it.
- No headset control commands, EQ, game/spatial/adaptive setters, firmware updater,
  reconnect reapplication or background service.
- No UI. This scaffold is the phone-side quality adapter only.

## Build prerequisites (not installed in this workspace)

A real Android build requires Android SDK platform 36, Gradle and the Kotlin plugin.
This workspace only has platform-tools, `d8`/`apksigner` and a JDK, so the Kotlin
sources are verified here by offline contract tests instead of compilation:

```sh
python -m unittest discover -s "research/realme-studio-h1" -p "test_*.py"
```

## Evidence

- Decompiled route: `research/realme-studio-h1/codec-changer-static-evidence.json`
- Installed 2.6.1 build: `research/realme-studio-h1/installed-261-static-evidence.json`
- Permission/version analysis: `research/realme-studio-h1/android-codec-evidence.json`
- Runtime round-trip: `research/realme-studio-h1/codec-quality-roundtrip.json`
- Scope and gates: `research/realme-studio-h1/APP-SCOPE.md`

## Safety contract

Values are policies, not measured bitrates. Unsupported actions stay disabled;
a remembered preference is not proof it was applied. Keep a user-initiated Settings
fallback. No root, Shizuku, re-signing, privileged grant or re-pairing is a dependency.
