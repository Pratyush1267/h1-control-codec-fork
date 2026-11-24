# Studio H1: full Android companion-app scope

Prepared 2026-11-24. **A build proposal, not an implemented Android app.**

## Goal

Build a fuller H1 companion app: working noise controls, an honest feature/status
dashboard, and Android codec-quality tools. Target every useful exposed capability,
without promising remote commands that the headset firmware or phone does not expose.

## What we can build, and what still needs discovery

| Feature | Available foundation | Custom-app scope / remaining gate |
|---|---|---|
| Battery and ANC / ambient / off | Agent-measured status; upstream noise commands; owner confirms ANC/ambient | Reuse attributed core, confirm responses, validate Off and reconnect behaviour |
| Game mode | Upstream incoming button-event parser; owner reports detection | Show last-reported state with timestamp; remote setter not established |
| Headset spatial audio | Upstream incoming button-event parser; owner reports detection | Same; distinguish the headset effect from Android spatialization or app DSP |
| Headset LDAC enablement | Hardware button documented; owner reports LDAC detection | Establish whether displayed LDAC is enablement or the negotiated codec; headset setter unverified |
| Adaptive noise | Owner reports detection; pinned README says state is not reported | Inspect actual app/version/display and isolate a state source; do not infer it from ANC or a label |
| Active codec and audio format | Upstream Android README documents codec in use and best supported | Use phone-side information where accessible; separate current, supported and requested values |
| LDAC playback quality | Android A2DP configuration and a no-root per-device authorization route exist; owner reports default Best Effort | Validate a direct no-root backend on the phone; retain Settings fallback and separately test reconnect reapplication |
| EQ and presets | Host-side Android EQ is a separate candidate | Label as phone-side DSP with player/path limits; headset-stored EQ unverified |
| Multipoint / firmware information | Manufacturer declarations; diagnostic candidates | Do not invent other-device state or firmware identity; no updater in the initial scope |

Evidence: [owner feedback](owner-control-validation.json),
[pinned H1 source inventory](h1-control-evidence.json),
[Windows measurement](windows-status-observation.json), and
[Android implementation evidence](android-codec-evidence.json).

“Detects” does not imply “can change remotely.” The existing code receives game
and spatial button events; replaying notifications is not a demonstrated setter.
The pinned README explicitly says no working game/spatial/LDAC/adaptive setters
were found. Its adaptive-state limitation and the owner's report remain separate
until the actual app build and displayed value can be checked.

## LDAC quality: a phone-side module

Keep these distinct:

1. **Headset supports/allows LDAC.** This is not proof that a connection uses it.
2. **Android negotiated LDAC for the active A2DP device.** This is not a quality policy.
3. **Playback quality policy:** Best Effort, quality-oriented, balanced or
   connection-oriented. Best Effort is adaptive, not “LDAC off” or necessarily
   always the lowest rate. A forced highest-rate policy can increase dropouts.
4. **Instantaneous encoded bitrate.** Do not label a configured policy or sample
   rate as a measured bitrate. Unknown throughput remains unknown.

The inspected AOSP Settings implementation defaults its quality preference to
the Best Effort option. It changes the active device's A2DP codec configuration,
not the H1's SPP noise commands. The codec getter/setter are `@hide` / `@SystemApi`,
not a stable ordinary public-SDK contract. On the originally retained current
AOSP snapshot, Bluetooth runtime permission alone does not satisfy their service
authorization; older inspected releases have different gates.

Current AOSP's service also accepts a matching Companion Device Manager
association instead of `BLUETOOTH_PRIVILEGED`. The association is scoped to the
calling package/UID and the requested device; it does not grant that privileged
permission. Android 16's official hidden-API flags also show these two methods
are **not blanket-blocked**, so the annotations alone do not imply root or a
restriction-bypass requirement. Neither fact makes the API stable or proves
access on every phone. Versioned source review includes an Android 15 snapshot
whose getter is privileged-only while its setter still accepts the association.
[Pinned evidence and access addendum](android-codec-evidence.json).

The owner supplied Bluetooth Codec Changer as a concrete counterexample and then
approved investigation of its unmodified APK on their phone. Follow
[the app analysis/test plan](CODEC-CHANGER-TEST-PLAN.md). APK strings are leads;
the app's actual call path and owner-phone results must be established separately.

### Proposed delivery tiers

- **Standard no-root app:** noise controls and supported status, plus a
  device-validated direct codec backend using normal Bluetooth permission and
  eligible companion consent where required. Unsupported actions stay unavailable;
  retain a user-initiated Settings shortcut as fallback, not the only possible route.
- **Compatibility validation:** establish actual API access, per-device
  authorization and observed reconfiguration before advertising direct quality
  selection on that Android/OEM build.
- **Optional elevated backend:** only if separately requested and necessary;
  root/Shizuku/system integration is a compatibility experiment, not a guaranteed
  solution or an initial dependency. It cannot create missing headset commands.

Remembering a desired quality profile and **actually reapplying it** are different.
An opt-in reconnect policy must verify the active device, LDAC negotiation,
available access, and the resulting configuration; report pending/failure/stale
states, avoid retry loops, and provide a user-initiated return to Best Effort.
No persistence has yet been tested on the owner's phone.

## Architecture and release boundaries

Use a Kotlin / Jetpack Compose Android app with a selectively reused MIT core:

```text
Dashboard, profiles and Quick Settings tiles
  -> capability-aware state model: current / last seen / pending / unknown
     -> one H1 RFCOMM session: status + validated headset commands
     -> Android audio-info adapter: active route, codec and supported formats
      -> Android quality adapter: validated no-root backend + Settings fallback
```

Every displayed value needs a source and freshness marker. Clear or mark
button-only cached values stale after reconnect; do not present them as a fresh
query. Keep requested settings separate from observed state. Strip arbitrary
payload/developer entrypoints from a consumer build; retain local redacted
diagnostics, not automatic cloud uploads or secret/raw pairing exports.

1. **Build foundation:** attributable protocol core, permission flow, transport
   lifecycle, confirmed noise controls and honest status dashboard.
2. **Add Android audio tools:** current-vs-supported codec/format information,
   no-root quality adapter, Settings fallback and safe profile UI. Unsupported
   actions stay disabled; remembering a preference is not proof it was applied.
3. **Validate quality control:** actual phone/OS permissions, one reversible
   policy change, audio continuity and reconnect/restart behaviour; only then
   offer opt-in automatic reapplication.
4. **Research remaining headset setters:** one feature and hypothesis at a time,
   private captures, timestamps and physical before/after/reversal evidence.
   Incoming events alone do not establish an outgoing command. No blind opcode
   sweep, firmware flash, reset or known-good pairing replacement.

## Implemented adapter scaffold

`android-app/` now contains an **uncompiled** Kotlin scaffold for the validated
phone-side quality backend: the four observed LDAC policies, the platform
constructor order, reflection-based get/set with explicit error states, a status
reader that keeps current/selectable/requested values separate, and a freshness-
aware UI state model. It also reuses the pinned MIT H1 Control RCSP framing,
parser and Bluetooth-free session core (`h1core/`), with the noise-mode setter
changed to keep a request pending until the headset reports it; no RFCOMM
transport was copied. A bounded Android shell probe could not open either
documented RFCOMM route while the H1 was connected over A2DP, so no Android
status bytes were observed; see
[android-rfcomm-observation.json](android-rfcomm-observation.json). This is a
negative result for that route under that identity, not proof that a real app
with companion consent cannot connect. `CompanionOnboarding` adds the user-approved association flow
and the read/write gates; see [COMPANION-ONBOARDING.md](COMPANION-ONBOARDING.md).
Pure JVM tests cover the association decisions, and offline contract tests in
`test_codec_contract.py` check that these facts still match the retained evidence.
A portable Windows toolchain compiled this scaffold, but the owner then asked to
extend the full open-source H1 Control app instead. That MIT-compliant fork now
builds with Gradle and is installed side-by-side as `dev.madhuraj.h1.fork`;
see [h1-control-fork-evidence.json](h1-control-fork-evidence.json).

## Smallest next step

Build and run the scaffold on a device after adding the **companion-device
onboarding flow** (the association is currently an existing user-approved
prerequisite, not something this scaffold creates). Then repeat the reversible
quality round-trip with our own app and test reconnect/HFP persistence before
offering opt-in automatic reapplication. Still obtain the earlier feature-test
app/version and a redacted adaptive-noise screenshot; that earlier report must
not be attributed to the newly supplied codec app. Optional elevated integration
is not an initial dependency. The completed app analysis and phone inventory are
in [the test plan](CODEC-CHANGER-TEST-PLAN.md); the runtime result is in
[codec-quality-roundtrip.json](codec-quality-roundtrip.json).
