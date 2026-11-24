# Companion association onboarding for our app

Design note. **Source scaffold only:** uncompiled, not installed, no phone changes.

## Why our app needs its own association

On Android 16, `setCodecConfigPreference` and (in the inspected snapshots) `getCodecStatus`
pass the Bluetooth service check only for `BLUETOOTH_PRIVILEGED` **or** a non-self-managed
CompanionDeviceManager association whose package and device MAC match the caller. A
Bluetooth bond alone does not qualify. The association observed on the owner's phone
belongs to Bluetooth Codec Changer, not to our app, so a fresh install of our app would
have no codec access unless the user approves a new association.

An association is a per-package, per-device authorization exception. It does **not**
grant `BLUETOOTH_PRIVILEGED`, and re-pairing the H1 can require re-association.
[Permission analysis](android-codec-evidence.json) ·
[Observed association and route](codec-changer-static-evidence.json) ·
[Installed 2.6.1 build](installed-261-static-evidence.json) ·
[Runtime round-trip](codec-quality-roundtrip.json)

## Official API shape used

- `CompanionDeviceManager.associate(AssociationRequest, Executor, Callback)`.
- `Callback.onAssociationPending(IntentSender)` is launched with
  `Activity.startIntentSenderForResult(...)`; the **system** shows device selection and
  consent. The app cannot silently approve it.
- `Callback.onAssociationCreated(AssociationInfo)` and `onFailure(CharSequence)`.
  `EXTRA_ASSOCIATION` may also arrive in the activity result.
- `getMyAssociations()` (API 33+) and the deprecated `getAssociations()` fallback.
- `AssociationInfo.getDeviceMacAddress()`, `isSelfManaged()`, `getId()`.
- Request builder: `addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(mac).build())`,
  `setSingleDevice(true)`, `setDisplayName(name)`, `setSelfManaged(false)`.
- Manifest: `uses-feature android.software.companion_device_setup` with
  `android:required="true"`. CDM checks `PackageInfo.reqFeatures` for this exact
  string; a not-required declaration or the hardware-style name is rejected.

Self-managed associations require `REQUEST_COMPANION_SELF_MANAGED` and are explicitly
rejected by the AOSP codec gate, so our scaffold does not request them.

## Version qualification

`AssociationPolicy` treats SDK 34+ as requiring an association for the setter and SDK 35+
for the getter, based on inspected AOSP snapshots (Android 12/13 had no such gate).
OEM/Mainline builds may differ; this must be validated on each target device, not assumed.

## Gates and boundaries

- No root, Shizuku, `BLUETOOTH_PRIVILEGED`, auto-grant, or re-signing.
- Do not disassociate the owner's existing app, reset or re-pair the H1, or change phone
  settings from this scaffold.
- `CodecController` checks an injected read/write gate before reflection; a remembered
  association or preference is never reported as a confirmed codec change.
- User consent stays on-device. Unknown, pending, self-managed-only and different-device
  states remain explicit instead of being collapsed into a generic failure.
- Full scope and remaining gates: [APP-SCOPE.md](APP-SCOPE.md) and
  [the test plan](CODEC-CHANGER-TEST-PLAN.md).
