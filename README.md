# H1 Control codec fork

Public source fork of [madhuraj0/h1-control](https://github.com/madhuraj0/h1-control)
pinned to commit `a7d08b045164996c7b5153660eb7d640898b3242` (MIT, Copyright (c) 2026 Madhuraj).

Package: `dev.madhuraj.h1.fork`. Installs side-by-side; an existing `dev.madhuraj.h1` is not replaced.

## What this adds

- Direct LDAC playback-quality selection using ordinary Android reflection and a
  user-approved CompanionDeviceManager association. No root, privileged permission
  or re-signing.
- Companion association onboarding with system consent.
- Status query on app open, a manual refresh button, and a timestamp on last-seen
  game/spatial button events.
- An experimental opt-in reapply-after-reconnect path. It is **not proven**: manual
  tap remains the verified route. Leave it OFF unless testing.
- Bounded, redacted evidence in `research/realme-studio-h1/`.

## Usage

### Install

1. Download `h1-control-codec-fork-release.apk` from the [GitHub release](https://github.com/Pratyush1267/h1-control-codec-fork/releases/tag/v0.1.0-codec-fork).
2. Open it on the phone and allow installation from this source.
3. Android 8.0+ (minSdk 26). Tested on Android 16 with a Realme TechLife Studio H1.

Release APK SHA-256: `2e4d88e1cb6f0b1f4fabe1fde135a91fedc350186f002bcb855a65513895d5f0`.
Signed v2/v3 with `CN=H1 Control Codec Fork`. Local release key, not an official publisher identity.

### First setup

1. Pair the Studio H1 in Android Bluetooth settings first.
2. Open this app. Grant **Bluetooth permission** when asked.
3. Tap the headset icon and choose the H1 from the paired-device list.
4. The app opens the control link and sends one status query. Battery and noise mode appear.
5. For LDAC quality control, tap **Associate this H1 to change quality** and approve the system dialog. This creates a companion association for this app only; it does not grant a privileged permission.

Re-pairing the H1 can require a new association.

### Noise control

- **Off / Transparency / ANC** buttons send the documented headset command.
- Changes made with the physical ANC button are not reported back. The UI can go stale until a status refresh or an app-set command.

### Audio quality

Visible when the active codec is LDAC and the companion association is present:

- **Audio quality (990/909 kbps)** — `codecSpecific1 = 1000`
- **Balanced (660/606 kbps)** — `1001`
- **Connection (330/303 kbps)** — `1002`
- **Best effort (adaptive)** — `1003`

Verified: `1000 ↔ 1002` changes were observed in Android's A2DP state. Values are configured policies, not measured radio throughput.

**Auto reapply after reconnect** is OFF by default and **experimental**. It did not demonstrate reliable automatic restoration. Do not rely on it; tap a quality button after reconnect if needed.

### Modes dashboard

- **Game mode** and **spatial audio** show the last button event received from the H1, with a `Last button report: Ns ago` marker. Toggle the feature on the headset to refresh. No verified remote setter exists.
- **Adaptive noise** is not reported by the headphones. The app shows `Not reported`.
- **LDAC** under Modes reflects Android's active codec, not the headset's hardware LDAC button state.

### Refresh

The **Refresh** button in the status card sends one status query. It cannot report game/spatial/adaptive state because those are not in the status response.

## Layout

- `h1-control-fork/` — full MIT source tree with the fork changes in
  `androidApp/src/main/kotlin/dev/madhuraj/h1/`.
- `research/realme-studio-h1/` — evidence, reports and offline verification scripts.
- `android-app/` — earlier standalone scaffold, kept for provenance.

## Build

Use Android SDK Platform 34, build-tools 34, JDK 17 and the included Gradle wrapper:

```sh
cd h1-control-fork
./gradlew :androidApp:assembleDebug
```

Release signing expects `~/.h1-control/keystore.properties` as in upstream. Without it, AGP produces an unsigned release APK; sign it with `apksigner` before distribution.

## Known limits

- Game/spatial values come only from headset button push events. Three bounded write
  experiments found no verified setter. Adaptive noise is not reported at all.
- No headset EQ, firmware update, game/spatial/adaptive setter, or headset-side LDAC toggle.
- Displayed bitrate is the configured policy, not measured radio throughput.
- LDAC quality can reset after disconnect/reconnect. Manual reapply is the verified path.
- Automatic quality reapply after reconnect is unresolved.

No APK, signing key, private capture or raw device log is included in this repo.
Upstream H1 Control is MIT licensed, Copyright (c) 2026 Madhuraj. See
`research/realme-studio-h1/THIRD-PARTY-NOTICES.md`.
