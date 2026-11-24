# H1 Control codec fork

Public source fork of [madhuraj0/h1-control](https://github.com/madhuraj0/h1-control)
pinned to commit `a7d08b045164996c7b5153660eb7d640898b3242` (MIT, Copyright (c) 2026 Madhuraj).

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

The fork installs side-by-side as `dev.madhuraj.h1.fork` so an existing
`dev.madhuraj.h1` installation is not replaced.

## Known limits

- Game/spatial values come only from headset button push events. Three bounded write
  experiments found no verified setter. Adaptive noise is not reported at all.
- Displayed bitrate is the configured policy, not measured radio throughput.
- Automatic quality reapply after reconnect is unresolved.

No APK, signing key, private capture or raw device log is included.
