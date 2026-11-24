# H1 Control codec fork — usage

Side-by-side fork of H1 Control with direct Android LDAC quality control. Package:
`dev.madhuraj.h1.fork`. It does **not** replace an existing `dev.madhuraj.h1` install.

## Install

1. Download `h1-control-codec-fork-release.apk` from the GitHub release.
2. Open it on the phone and allow installation from this source.
3. Android 8.0+ (minSdk 26). Tested on Android 16 with a Realme TechLife Studio H1.

Release APK SHA-256: `2e4d88e1cb6f0b1f4fabe1fde135a91fedc350186f002bcb855a65513895d5f0`.
Signed v2/v3 with `CN=H1 Control Codec Fork`. This is a local release key, not an official publisher identity.

## First setup

1. Pair the Studio H1 in Android Bluetooth settings first.
2. Open this app. Grant **Bluetooth permission** when asked.
3. Tap the headset icon and choose the H1 from the paired-device list.
4. The app opens the control link and sends one status query. Battery and noise mode appear.
5. For LDAC quality control, tap **Associate this H1 to change quality** and approve the system dialog. This creates a companion association for this app only. It does not grant a privileged permission.

If the H1 is already associated, the button is unnecessary. Re-pairing the H1 can require a new association.

## Noise control

- **Off / Transparency / ANC** buttons send the documented headset command.
- Changes made with the physical ANC button are not reported back. The UI can go stale until a status refresh or an app-set command.

## Audio quality

Visible when the active codec is LDAC and the companion association is present:

- **Audio quality (990/909 kbps)** — `codecSpecific1 = 1000`
- **Balanced (660/606 kbps)** — `1001`
- **Connection (330/303 kbps)** — `1002`
- **Best effort (adaptive)** — `1003`

Verified on the owner's phone: `1000 ↔ 1002` changes were observed in Android's A2DP state. Values are configured policies, not measured radio throughput.

**Auto reapply after reconnect** is OFF by default and **experimental**. It did not demonstrate reliable automatic restoration in testing. Do not rely on it; tap a quality button after reconnect if needed.

## Modes dashboard

- **Game mode** and **spatial audio** show the last button event received from the H1, with a `Last button report: Ns ago` marker. Toggle the feature on the headset to refresh. There is no verified remote setter.
- **Adaptive noise** is not reported by the headphones. The app shows `Not reported`.
- **LDAC** under Modes reflects Android's active codec, not the headset's hardware LDAC button state.

## Refresh

The **Refresh** button in the status card sends one status query. It cannot report game/spatial/adaptive state because those are not in the status response.

## Known limits

- No headset EQ, firmware update, game/spatial/adaptive setter, or headset-side LDAC toggle.
- LDAC quality can reset after disconnect/reconnect. Manual reapply is the verified path.
- Bitrate labels are policies, not throughput measurements.
- Auto reapply is unresolved.

## Build from source

```sh
cd h1-control-fork
./gradlew :androidApp:assembleRelease
```

Release signing in this repo expects `~/.h1-control/keystore.properties` as in upstream. Without it, AGP produces an unsigned release APK; sign it with `apksigner` before distribution.

Upstream H1 Control is MIT licensed, Copyright (c) 2026 Madhuraj. See `research/realme-studio-h1/THIRD-PARTY-NOTICES.md`.
