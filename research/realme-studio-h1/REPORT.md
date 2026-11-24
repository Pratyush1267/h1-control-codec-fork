# Realme TechLife Studio H1: companion-app research and runtime report

> Generated 2026-11-24 | Depth: default, five aspects | Indexed source URLs: 38 | Quality: WARN

## Executive summary

**A genuine, limited companion app is feasible, and an unofficial Android app
already exists.** [H1 Control][S13], released as [v0.1.0 on 2026-11-12][S14],
implements Off/ANC/Transparency control and battery reporting over Bluetooth
SPP/RFCOMM. Its Kotlin source and author-labelled hardware fixtures are stronger
evidence than a feature list alone. Its firmware 2.4.0 packet fixtures remain
author-reported; the owner has now separately reported working ANC and ambient
functions, without specifying test versions. [S15][S17][S18]
[Owner feedback](owner-control-validation.json).

**This investigation independently verified the status path on the owner's
already-connected Windows headset.** One 14-byte source-backed query received
a matching successful 96-byte response: **39% battery**, reported noise mode
**off**, and a list containing **off/ANC/transparency**. The socket then closed;
no setters, pairing changes or retries were used. This is a live protocol result,
not merely a cached service UUID. See [the observation](windows-status-observation.json)
and [offline verification](windows-status-verification.json). No setter was
executed by the agent. **The owner subsequently confirmed ANC and ambient/
transparency work.** That functional report is separate from the Windows packet
measurement; app/platform/firmware details and versioned repeat trials remain
unspecified. [Owner feedback](owner-control-validation.json).

The owner also reports detection of **game mode, spatial audio, LDAC, adaptive
noise and the codec in use**, and notes Android's default **Best Effort** LDAC
quality setting. Detection is separate from remote setting control. The expanded
Android target and phone-side API limits are now in [APP-SCOPE.md](APP-SCOPE.md),
with [pinned AOSP evidence](android-codec-evidence.json). This is a later
supplement to the initial 38-source synthesis, not a completed APK or a new
owner-phone codec measurement.

**Official realme Link support is unresolved.** Launch reviews and later owner
posts report its absence, while two current OEM FAQs include H1 in feature lists
alongside shared realme Link instructions. Those implications must not be erased,
but neither FAQ demonstrates H1 in a current app's device catalogue. [S4][S5][S8][S11][S12]
For the Android target, evaluate H1 Control before rebuilding; use Wavelet or
Poweramp only for clearly labelled **phone-side EQ**, not headphone-stored EQ.
[S14][S20][S22]

### Key insights

1. **An absent official app does not imply an inaccessible headset.** OEM SPP
   declarations, model-specific protocol code, captured fixtures and the local
   Windows response converge on a usable status interface. They do not jointly
   prove untested setters. [S2][S15][S17]
   [Local measurement](windows-status-observation.json).
2. **Reuse beats speculative reconstruction for Android.** A model-specific
   release, Kotlin core, Android transport and documented limitations already
   exist. A selective fork can focus on confirmed state, timeouts and UI rather
   than borrowing opcodes from unrelated Realme Buds. [S13][S14][S15][S18]
3. **Several apparent “H1 apps” solve a different problem.** Wavelet, Poweramp,
   Equalizer APO and the H1-branded Windows enhancer process source audio; that
   is not persistent headphone EQ or built-in ANC/spatial control. [S20][S22][S25][S19]
4. **The official-support contradiction is actionable, not resolvable by a vote.**
   Shared OEM feature guides conflict with model-specific no-app reporting and
   integration requests. The missing evidence is a current India-region,
   versioned app catalogue and working H1 settings page. [S4][S5][S8][S12]

## 1. Official support and the contradiction

| Candidate | Supported conclusion as of this investigation |
|---|---|
| realme Link | Unresolved: positive shared-FAQ implications, contrary hands-on/owner reports; no current app pairing test |
| HeyMelody | Publisher describes OPPO/OnePlus earbuds; no retrieved H1 confirmation |
| Google Fast Pair | Officially advertised pairing feature and locally observed message-stream service UUID; not an official-app compatibility declaration |
| Website “Download App” banner | Shopping-app destination, not evidence of headset management |

Sources: realme FAQs [S4][S5], app publisher listings [S6][S7], review [S8],
community posts [S11][S12], official product page [S1], and
[saved official product evidence](official-evidence.json).

### Evidence on both sides

- **2024-10-19:** Beebom's week-long hands-on review states that H1 “doesn't have
  companion app support” and describes using Wavelet as an EQ workaround. It
  does not identify a versioned app/phone/firmware discovery test. [S8]
- **2025-09-12 and 2026-03-08:** owners request realme Link integration. These are
  self-reports, without reproducible app-versioned compatibility evidence.
  A response saying feedback was shared is not a delivery announcement. [S11][S12]
- **2026-01-30 API update fields:** “360° Spatial Audio Effect” says, “You can
  connect your phone and turn it on in realme link,” then includes Studio H1 in
  its supported-feature list. A dual-device FAQ likewise includes H1 and gives
  a shared step to enable dual-device connection in Link. Neither provides an
  H1-specific pairing procedure, screenshot or app version. [S4][S5]
- **2026-11-19:** Geekman's roundup still says “There is no app or EQ,” but does
  not document a fresh current-app retest. Treat it as weak current negative
  evidence, not a compatibility inventory. [S10]

The FAQ HTML sometimes omitted article bodies. The official public
`customer-api.realme.com/in/knowledge/articleDetail` POST API supplied them.
[official-app-faq-evidence.json](official-app-faq-evidence.json) retains request
parameters, exact excerpts and literal timestamps. These timestamps contain
**no timezone** and do not date an H1 addition or the start of app support.

The manufacturer’s button documentation establishes physical controls, not
the absence of parallel app controls. The site's shopping-app banner and BLE/SPP
profiles must likewise not be used to settle compatibility. [S1][S2][S3]

## 2. Existing alternatives: three different layers

| Tool | Platform/scope | What is supported by inspected evidence | What it does not establish |
|---|---|---|---|
| H1 Control v0.1.0 | Android; Apple-silicon macOS | Headset-directed noise-mode code, battery reads, last-reported game/spatial status; Android codec/format information | Windows release; extra remote setters; versioned owner-unit setter capture; headphone EQ or updater |
| H1-branded Windows Enhancer | Windows + Equalizer APO | Writes host APO EQ, crossfeed and delay configuration | Headset protocol or built-in spatial control |
| Wavelet | Android source audio | Manual nine-band EQ, AutoEq/custom imports and per-device host settings | H1 ANC or headset-stored EQ |
| Poweramp Equalizer | Android source audio | Flexible graphic/parametric EQ and player-dependent processing | H1 vendor control or a verified H1 correction profile |
| Equalizer APO + Peace | Windows source audio | Windows audio-effects filtering and preset UI | H1 mode commands; processing in paths that bypass APO |
| Gadgetbridge | Model-specific headphones | Realme Buds models listed; H1 absent from inspected official list | Actual H1 compatibility or universal incompatibility |
| Alternative A2DP Driver | Windows Bluetooth transport | Vendor advertises LDAC and replacement A2DP driver | H1 ANC/EQ; compatibility with this host/radio |

Sources: [S13][S14][S15][S19][S20][S21][S22][S24][S25][S26][S27].
No app, APK, audio driver or APO configuration was installed or changed by the
agent. The owner's subsequent functional report is recorded separately.

### H1 Control: useful but partial

The inspected release documents Android **8.0+** and macOS **13+ on Apple silicon**.
The author reports testing firmware **2.4.0**, one Android 15 phone and one
Apple-silicon Mac on macOS 26. No examined Windows build exists. [S13][S14]

The source actually opens RFCOMM and writes/reads bytes; fixture tests include
a noise-mode setter acknowledgement and subsequent status push. This supports
a genuine model-specific controller, not a simulated “Synced” UI. [S15][S17][S18]
The setter implementation also updates displayed mode optimistically before
confirmation. A safety-oriented fork should instead expose **pending**, matched
acknowledgement/push, failure and stale states. [S15]

### Owner follow-up: ANC and ambient work

After the recommendation to test the Android app, the owner replied:

> "yes noise controll such as anc and ambient mode works"

This is **owner-reported confirmation of those two functions**, recorded in
[owner-control-validation.json](owner-control-validation.json). The app/tool and
platform were not explicitly restated, nor were OS, app or firmware versions,
trial counts, an Off-mode test or restoration details. Do not silently assign
the upstream author's firmware 2.4.0 or Android 15 to the owner's test. It is not
an agent-performed Windows setter test or a packet-level command capture.

The next message reports additional detection of game mode, spatial audio,
LDAC, adaptive noise and the active codec. Actual displayed values and their
source/freshness were not supplied. This strengthens the proposed status scope,
not the evidence for remotely changing those settings.
[Owner feedback](owner-control-validation.json).

Limitations matter:

- Game mode, spatial audio, LDAC enablement and adaptive noise remain **button-only**
  in the documented implementation; no working setters were found. [S13]
- The pinned README documents Android codec information but explicitly says
  adaptive-noise state is not reported. The owner's adaptive-detection report
  needs reconciliation against the actual app/version/display; neither claim
  should be silently converted into a verified adaptive-state implementation.
  [S13] [Owner feedback](owner-control-validation.json).
- Physical ANC-button changes are not reported, so displayed noise mode can be
  stale. One **control connection** is accepted at a time, distinct from audio
  multipoint. [S13]
- The eight-byte noise-mode gain block is **not** evidence of music-EQ programming.
  Generic JieLi SDK support for ANC/EQ does not prove those commands work on H1.
  [S15][S37]

### EQ: what can be recommended honestly

Wavelet and Poweramp are viable candidates for **manual Android sound tuning**,
subject to phone/player/audio-path compatibility. Wavelet documents Android and
Bluetooth/sample-rate exceptions; LDAC plus processing is not guaranteed on every
phone. Equalizer APO may be bypassed by ASIO or WASAPI-exclusive playback. [S20][S21][S22][S25]

The complete retrieved AutoEq results index contained **6,048 lines** with no
Studio H1 or TechLife entry. Other manufacturers' “H1” results are different
headphones. This is a bounded upstream-index finding, not an inspection of every
shipped Wavelet database or private user profile. No measured H1 correction
profile or headset-stored music-EQ implementation was verified. [S23]

The Windows Enhancer backend replaces Equalizer APO's configuration content.
It must not be run against a user's existing APO setup without preserving that
configuration. Its H1 branding does not turn host DSP into headset control. [S19]

## 3. Hardware and local controls

The official India specification identifies **RMA2411**, Bluetooth **5.4**,
profiles **`HFP/AVRCP/A2DP/BLE/SPP`**, and **AAC/SBC/LDAC** codecs. These are
manufacturer declarations; the printed label on the owner's unit was not inspected.
All **26 desktop marketing images** were reviewed and retained with hashes.
[S1][S2] [Evidence record](official-evidence.json).

| Official button action | Documented function |
|---|---|
| ANC, one short press | Awareness / ANC on / ANC off |
| ANC, hold 1 second | Game / music mode |
| ANC, three presses | LDAC on/off |
| ANC, two presses | Spatial effect on/off |
| ANC + volume-up, hold 1 second | Adaptive noise mode on/off |

Source: [official button image][S3]. GizNext's hands-on review corroborates the
main physical-button functions, but its smooth multipoint experience conflicts
with Beebom's reconnection difficulties. Neither account is a versioned app
compatibility test. [S9][S8]

A manufacturer-branded regional manual also documents multipoint pairing and
wired operation. [S28] The manufacturer manual in the FCC filing says game mode
is not supported with LDAC and spatial effect is not supported in wired mode. [S33] The
supplied USB-C audio cable's “not for charging” restriction concerns that cable,
not a verified universal restriction on the port. The recovered manual is regional,
not an independently located India-specific paper manual. [S28]

FCC exhibits for **2AUYFRMA2411** document separate BLE/classic-radio testing.
The retrieved BLE test report gives sample hardware **V1.3** and software
**V0.0.5**. Those are certification-sample identifiers, **not the owner's retail
firmware or latest firmware**. Internal photos exist, but no chip marking was
reliably verified in this investigation. [S29][S30]

H1 Control's author identifies **JieLi AC697**. Treat that as an attribution to
the project, not an independent chipset identification of this unit. Matching
RCSP packets do not establish the chip variant or firmware number. [S13]

## 4. Actual Windows runtime evidence

### Cache discovery

Windows' native cache reports one exact-name connected, remembered, authenticated
headset and seven installed/enabled services, including SPP
`00001101-0000-1000-8000-00805f9b34fb` and Fast Pair Message Stream
`df21fe2c-2515-4fdb-8886-f12c4d67927c`.
[Initial observation](windows-cache-observation.json).
Microsoft documents this API as enabled-service enumeration, **not a fresh
complete SDP database**. [S31] Google's specification identifies the message-stream
UUID, but service presence does not prove optional Fast Pair ANC extensions. [S34][S35]

### One source-backed query

After owner authorisation, the probe connected by SPP UUID with port zero;
Windows resolved the service using the documented native mechanism. No RFCOMM
channel was guessed. The resolved channel was not successfully recorded, so
the project author's channel **1** remains unverified locally. [S32][S13]

```text
Request, 14 bytes: FE DC BA C0 07 00 06 01 FF FF FF FF FF EF
Reply: 96 bytes; RCSP response opcode 0x07; sequence 1; status 0
Decoded: battery 39%; reported noise mode off; list off / ANC / transparency
```

The request is exactly the upstream fixture's `Commands.getStatus(1)`. The response
matched opcode and sequence and passed frame/TLV validation. The original private
bytes were re-parsed offline and agree with the public observation. [S15][S16][S17]
[Observation](windows-status-observation.json).
[Verification](windows-status-verification.json).

Exactly **one vendor send call** occurred. No automatic ACK, retry, setter,
pairing/service change, Bluetooth inquiry or GATT operation was requested.
The successful query required no application-level authentication handshake,
though ordinary Bluetooth pairing was already authenticated. This does not prove
that every untested feature lacks authentication.
[Observation](windows-status-observation.json).

The control socket closed. A later cache check still reports connected/remembered/
authenticated with the same seven UUIDs. Audio continuity and the physical noise
mode were not measured. [Later observation](windows-cache-after-status.json).
The three reported modes are **not three ANC-strength levels** and are not proof
that setters succeeded.

### Fast Pair is a separate branch

Google documents optional hearable-control setters and account-key-based message
authentication. Windows' ordinary authenticated pairing is not possession of that
account key. Our successful SPP/RCSP status query used neither the Fast Pair socket
nor its authentication protocol. Fast Pair account-key concerns therefore must
not be assumed to block this verified **SPP status read**. [S34][S35][S36][S16]

## 5. Engineering decision and next test

### Recommended route

1. **Reuse working noise control before rebuilding.** The owner now reports ANC
   and ambient working; record the actual app/tool, phone model, OS and app version
   if a reproducible compatibility record or custom build is wanted.
   [Owner feedback](owner-control-validation.json). The examined candidate is the
   [v0.1.0 release][S14].
   It is an unofficial GitHub APK, not an OEM/Play Store endorsement. No release
   binary signing or runtime-safety audit was performed here. Do not disable
   platform malware warnings to force installation.
2. **Complete formal validation only if extending/distributing controls.** ANC
   and ambient have an owner functional report, not versioned repeat-trial logs.
   Record the initial *physical* mode, check Off as well, verify reversal and
   repeat/reconnect behaviour before claiming stable multi-firmware support.
   Do not restore from possibly stale UI state. The Windows probe is not holding
   the control connection. [S13][S15]
3. **Fork selectively if necessary.** Reuse the MIT core with attribution,
   implement confirmed/pending/stale/error states, and keep unverified features
   absent or clearly unavailable. Android RFCOMM and Bluetooth permissions already
   have a source-backed route. [S13][S15][S18][S38]
4. **Resolve official compatibility separately.** Check the current India-region
   realme Link catalogue and a genuine H1 settings page, not merely audio pairing.
   Record app/OS versions and the actual failure/success. [S4][S5][S6]

The initial `adb`, `gradle`, `java` and `javac` command lookup returned no matches.
That does not prove tools exist nowhere; it means a local Android build environment
was not established. Nothing was installed, and **no Android APK was built**.
Windows diagnostic code used Python's standard library only.

### Expanded Android target and LDAC quality

The owner wants a fuller app beyond noise control. [APP-SCOPE.md](APP-SCOPE.md)
separates headset commands, last-reported feature events and phone-side audio
configuration. [android-codec-evidence.json](android-codec-evidence.json) records
five pinned AOSP files: Settings defaults its LDAC quality option to Best Effort
and calls the A2DP codec setter. That getter/setter is hidden/system API with a
privileged-or-companion-association service permission boundary, not an
unrestricted public SDK setting. Versioned source review found different gates
across Android 12-16, including an Android 15 snapshot with a privileged-only
getter.

The owner then supplied Bluetooth Codec Changer as a working counterexample and
approved analysis of the **unmodified app on their own phone**. The selected
phone is a Nothing A001 running Android 16, and the app was already installed as
version 2.4.1 from Play (owner-reported). The existing installation was
preserved and pulled read-only; no app was launched, replaced or re-signed.
[Static evidence](codec-changer-static-evidence.json) shows the concrete
rootless route: ordinary `Class.getMethod` reflection calls the hidden-but-public
`getCodecStatus`/`setCodecConfigPreference` methods; the codec config is built
through a hidden constructor; authorization comes from an existing, user-approved
CompanionDeviceManager association matching the app and the H1. The app requests
no `BLUETOOTH_PRIVILEGED`, and Android 16's official hidden-API flags list those
methods as `sdk,system-api,test-api`, so reflection is not blocklisted on this
build. A walkthrough and gates are in
[the analysis/test plan](CODEC-CHANGER-TEST-PLAN.md).

Implement the same no-root route for a fuller app: normal `BLUETOOTH_CONNECT`
plus per-device companion consent, reflected codec calls with graceful fallback,
and explicit quality-policy values. Keep a Settings shortcut as fallback. The
owner then completed one reversible round-trip on the updated 2.6.1 app:
LDAC policy 1000 (HIGH/990) -> 1001 (MID/660) -> 1000 (HIGH/990), verified
read-only. See [the round-trip evidence](codec-quality-roundtrip.json). This is
an observed app-driven result, not a measured radio bitrate or a test of
reconnect persistence. The owner's updated Play 2.6.1 build was then pulled
read-only through ADB and decompiled: it keeps the same reflection route and
quality constants, and its signer matches the retained 2.6.1 artifact.
See [installed 2.6.1 evidence](installed-261-static-evidence.json).
No root, Shizuku, privileged grant, re-signing or headset re-pairing was used.
Headset LDAC enablement is not the same as Android's active codec or LDAC
quality.

### Proposed architecture

```text
Android UI or future Windows UI
  -> capability/state model (unknown, stale and pending stay explicit)
  -> one lifecycle-managed RFCOMM transport to the selected owned headset
  -> attributed H1 RCSP parser / allowlisted status command
  -> optional, separately validated noise-mode setters
```

**Go:** status functionality, now locally verified. **Go:** reuse/evaluate the
existing noise-control implementation, supported by the owner's ANC/ambient report.
**Conditional go:** a custom or distributed control build after versioned repeat,
reversal and reconnect validation. **No implementation promise:** music EQ,
game/spatial/LDAC/adaptive setters or firmware updates without new evidence.
[S13][S15][S17] [Owner feedback](owner-control-validation.json).
This is a scope decision, not a claim those features are impossible.

## 6. Deliverables and validation

| File | Purpose |
|---|---|
| `windows_probe.py` | Cache-only metadata; no inquiry, sockets or vendor packets |
| `h1_status_probe.py` | Offline by default; explicit one-query execution; no setters |
| `rcsp_status.py` | Bounded offline parser; sequence matching; strict TLVs and unknown states |
| `verify_saved_evidence.py` | Offline byte/hash and interpretation verification |
| `windows-status-observation.json` | Redacted original live result |
| `windows-status-verification.json` | Exact-file digest and offline interpretation corroboration |
| `owner-control-validation.json` | Exact owner ANC/ambient confirmation and unspecified test details |
| `APP-SCOPE.md`, `android-codec-evidence.json` | Expanded feature target, owner detection and Android LDAC quality/API limits |
| `codec-changer-static-evidence.json`, `CODEC-CHANGER-TEST-PLAN.md` | Decompiled rootless codec route, phone inventory and reversible test gates |
| `codec-quality-observation.json`, `codec-quality-roundtrip.json` | Observed starting policy and completed 1000 -> 1001 -> 1000 app-driven round-trip |
| `windows-cache-observation.json`, `windows-cache-after-status.json` | Before/after cache observations |
| `official-evidence.json`, `official-app-faq-evidence.json` | Official declarations and conflicting FAQ wording |
| `h1-control-evidence.json`, `THIRD-PARTY-NOTICES.md` | Pinned-source hashes, provenance, scope and MIT notice, including the Kotlin core reuse |
| `WINDOWS-FINDINGS.md`, `DISCOVERY-PLAN.md` | Reproduction instructions and remaining gates |
| `android-app/` | Kotlin scaffold for the no-root codec adapter, companion onboarding and their tests |
| `android-build-evidence.json` | Portable Windows build receipt, APK identity and signature verification |
| `android-install-observation.json` | Owner-approved install and crash-free launch of the minimal test activity |
| `h1-control-fork-evidence.json` | MIT-compliant full-app fork with direct LDAC quality controls, built and installed side-by-side |
| `fork-quality-observation.json` | Fork association and observed LDAC policy 1002 after owner interaction |
| `rea-game-spatial-confirmation.json` | REA decompilation confirming game/spatial are push-event-only, not queryable |
| `game-mode-research.json` | Bounded public-source negative for any game/spatial read command |
| `game-mode-push-observation.json` | Owner-driven button toggle producing `RealmeEvent(04 01)` on H1 |
| `adaptive-gesture-observation.json` | Bounded negative: no adaptive frame on RFCOMM ch1 after ANC+ gesture |
| `reconnect-quality-observation.json` | LDAC policy reset from 1000 to legacy 3 after owner reconnect |
| `auto-reapply-final-observation.json` | 1000 active after later reconnect; automatic path not proven by logs |
| `game-setter-probe-1.json` | First bounded game-mode write hypothesis; ACK only, no observed state change |
| `game-setter-probe-2.json` | Second bounded game-mode hypothesis (`C0 02 05 01`); ACK only, no observed state change |
| `game-setter-probe-3.json` | Third bounded hypothesis, unwrapped realme frame; no response, no observed state change |
| `COMPANION-ONBOARDING.md` | User-consent association design, official API shapes and version gates |
| `android-rfcomm-observation.json` | Bounded Android RFCOMM socket negative; no status bytes sent |

**64 offline tests passed on this host.** The suite tests framing, all fixture split boundaries, malformed input,
wrong sequence, no automatic ACK/retry, offline defaults, output-file protection,
Windows ABI layout, hash/newline handling, and that the Android scaffold's codec
values, constructor order, companion gates and permission declarations match the
validated evidence. Pure JVM association-policy tests are also present but need a
Kotlin/Gradle environment to run. The offline suite does **not** compile Kotlin,
test real ANC setters, or prove headset commands.
See `test_*.py`; run:

```powershell
python -m unittest discover -s "research/realme-studio-h1" -p "test_*.py" -v
```

The raw live capture and eight unchanged upstream source files remain outside the
repository in the approved private temporary directory. Source hashes were verified
offline. The exact original capture-file digest is
`5dd8c4f925a5f972780f7e579ab52598e3708cd8d538e7b7a0edb151a7bf8a20`.
The original observation's digest covered LF-normalised JSON before Windows wrote
CRLF; the verification addendum records both scopes without rewriting either
original. Future writes explicitly use LF and have an offline regression test.
No live query was repeated to fix this logging issue.

## 7. Gaps, limits and devil's advocate

- **Search coverage is not exhaustive.** Five parallel aspects completed, but
  all four planned WebSearch queries per aspect failed with **HTTP 402**; Exa was
  unavailable. Direct manufacturer/app-publisher retrieval, GitHub, community
  APIs and specific press sites supplied evidence. Negative findings are bounded
  to those inspected sources, not the whole web.
- **Retrieval obstacles:** direct FCC attachments returned HTTP 403, so primary
  exhibits were read through FCCID.io. A later local GitHub release-API capture
  hit a rate-limit 403 after retaining all eight pinned source files. These are
  access limits, not proof of absent manuals, releases or firmware.
- **Official support:** no current realme Link/HeyMelody catalogue or pairing
  test was conducted, and support contacts were not contacted.
- **Agent-measured scope:** one status query only; no agent setter test, BLE
  inventory, full SDP inventory or resolved RFCOMM channel measurement. Firmware
  and printed model remain unverified. The owner reports ANC/ambient working and
  additional status detection; actual app/tool, versions, freshness, Off test,
  repeated trials and reversal details were not supplied. Android quality
  control/persistence was not tested. [Owner feedback](owner-control-validation.json).
- **Model-specific limits:** no verified headphone-stored music EQ or updater;
  published H1 Control packet-level setter compatibility comes from one author
  and limited reported test devices. The owner functional report does not
  establish a firmware revision or firmware-history causal claim.
- **Private data:** addresses are redacted in published files. Unknown protocol
  attributes remain length-only in public observations; raw bytes are private.

### Devil's advocate

The strongest objection is that a valid status reply is easier to establish than
reliable mode control across unknown firmware, reconnects and multipoint use.
That objection is correct: this report distinguishes **agent-measured status**,
**owner-reported working ANC/ambient**, and unverified versioned setter capture.
It does not claim full OEM-equivalent control. Packet-level setter fixtures still
come from the upstream author. Shared OEM FAQs could reflect real support changes or generic instructions;
neither explanation is proven. Failed broad searches also create selection bias
toward directly accessible repositories and publications.

## 8. Methodology and quality gate

The completed aspects were **official compatibility**, **manuals/hardware**,
**independent testing**, **existing alternatives**, and **custom-app feasibility**.
Results were deduplicated by URL and claim; multiple files/images from one project
were not counted as independent hardware confirmations. Local runtime evidence was
kept separate from manufacturer declarations and maintainer reports. The later
owner confirmation is a separate functional evidence item, not a new web source.

- **Sources evaluated:** at least the 38 indexed URLs below; the wider rejected
  candidate set was not completely counted. **Sources included:** 38.
- **Tier distribution:** S:33, A:0, B:2, C:2, D:1. S denotes primary docs/source,
  not that every maintainer hardware claim is independently proven. B community
  self-reports remain lower-confidence; C hands-on press and D roundups are distinct.
- **Recency:** publication/version dates were used when available, not substituted
  with retrieval dates. Undated living documentation is labelled as such; FAQ
  update fields do not establish feature-launch dates. All sources accessed 2026-11-24.

Transparent heuristic coverage checklist, scored answered=1, partial=0.5,
unresolved=0:

| Core question | Score | Reason |
|---|---|---|
| Current official app support | 0.5 | Contradictory evidence, no versioned app test |
| Existing H1-specific unofficial app | 1 | Release, code and fixture inspection |
| Owner-unit vendor status interface | 1 | Matched live response and offline verification |
| Owner-unit noise-mode setters | 0.5 | Owner confirms ANC/ambient; app/method/versions and repeat/reversal details unspecified |
| Headphone-stored music EQ | 0 | No verified implementation |
| Other remote feature setters | 0.5 | Published limits; absence not exhaustively proven |
| Hardware/firmware identity | 0.5 | Official model and lab identifiers; owner firmware unknown |
| Android implementation readiness | 0.5 | Existing source/release; local build environment not established |

Quality metrics are disclosed heuristics, not empirical accuracy probabilities:

| Metric | Result | Calculation |
|---|---|---|
| Saturation | 56.25% | 4.5 / 8 checklist points |
| Diversity | 0.500 | 19 labelled publishing groups / 38 indexed URLs; not a guarantee of independent testing |
| Tier quality | 0.926 | (33×1 + 2×0.6 + 2×0.4 + 1×0.2) / 38 |
| Evidence depth | 4 items/insight | Four key insights with four source/evidence findings each; local measurement identified separately |
| Weighted quality score | **0.747 — WARN** | 0.3×0.5625 + 0.2×0.5 + 0.3×0.9263158 + 0.2×min(4/3,1) |

The owner confirmation strengthens the functional finding but does not complete
the versioned validation checklist, so its conservative partial score is retained.
No critical source-count/insight/primary-source failure is present. WARN is retained
because the score is below 0.8 and broad search, official-app retesting and local
setter verification are incomplete.

## Sources

| # | Source | Tier | Publishing group | Used in |
|---|---|---|---|---|
| S1 | [Official India product page][S1] | S | realme | Official, hardware |
| S2 | [Official specifications image 24][S2] | S | realme | Protocol leads, hardware |
| S3 | [Official controls image 23][S3] | S | realme | Local controls |
| S4 | [Spatial-audio FAQ][S4] | S | realme | Official contradiction |
| S5 | [Dual/triple-device FAQ][S5] | S | realme | Official contradiction |
| S6 | [realme Link Play listing][S6] | S | realme | Official scope |
| S7 | [HeyMelody Play listing][S7] | S | HeyTap | Official scope |
| S8 | [Beebom hands-on review][S8] | C | Beebom | Historical support, EQ |
| S9 | [GizNext hands-on review][S9] | C | GizNext | Independent controls/context |
| S10 | [Geekman headphone roundup][S10] | D | Geekman | Current weak negative |
| S11 | [2025 owner integration request][S11] | B | realme community | Official-support uncertainty |
| S12 | [2026 owner integration request][S12] | B | realme community | Official-support uncertainty |
| S13 | [H1 Control README/project][S13] | S | H1 Control | Scope, limitations, build |
| S14 | [H1 Control v0.1.0 release][S14] | S | H1 Control | Existing Android/macOS app |
| S15 | [Pinned command/state implementation][S15] | S | H1 Control | Status/setter semantics, UI |
| S16 | [Pinned RCSP framing][S16] | S | H1 Control | Protocol |
| S17 | [Pinned captured-fixture tests][S17] | S | H1 Control | Protocol evidence |
| S18 | [Pinned Android RFCOMM transport][S18] | S | H1 Control | Actual headset I/O |
| S19 | [Pinned Windows Enhancer backend][S19] | S | Windows H1 Enhancer | Host-EQ distinction |
| S20 | [Wavelet features][S20] | S | Wavelet | Android EQ |
| S21 | [Wavelet troubleshooting][S21] | S | Wavelet | Audio-path/profile limits |
| S22 | [Poweramp Equalizer Play listing][S22] | S | Poweramp | Android EQ |
| S23 | [Complete AutoEq results index][S23] | S | AutoEq | Bounded profile finding |
| S24 | [Gadgetbridge Realme list][S24] | S | Gadgetbridge | Model-specific support |
| S25 | [Equalizer APO project][S25] | S | Equalizer APO | Windows source DSP |
| S26 | [Peace project][S26] | S | Peace | Windows source DSP |
| S27 | [Alternative A2DP Driver vendor][S27] | S | BluetoothGoodies | Codec/transport distinction |
| S28 | [Manufacturer-branded regional manual][S28] | S | realme | Controls, wired/mode limits |
| S29 | [Primary FCC BLE report, mirrored][S29] | S | FCC filing | Radio/lab identifiers |
| S30 | [Primary FCC internal photos, mirrored][S30] | S | FCC filing | Chipset-identity limit |
| S31 | [Windows installed-service API][S31] | S | Microsoft | Cache scope |
| S32 | [Windows Bluetooth connect][S32] | S | Microsoft | Native UUID resolution |
| S33 | [Manufacturer FCC manual, mirrored][S33] | S | realme | Physical controls and mode constraints |
| S34 | [Fast Pair Message Stream][S34] | S | Google | Separate transport |
| S35 | [Fast Pair Hearable Controls][S35] | S | Google | Optional ANC extension |
| S36 | [Fast Pair MAC authentication][S36] | S | Google | Account-key boundary |
| S37 | [JieLi official Android RCSP SDK][S37] | S | JieLi | Generic vs H1 capability |
| S38 | [Android Bluetooth permissions][S38] | S | Google | Android implementation |

[S1]: https://www.realme.com/in/more-products/realme-techlife-studio-h1
[S2]: https://image01.realme.net/general/20240826/1724641472845b7951f5e8f7c4b3ab95b04165d33e20d.jpg
[S3]: https://image01.realme.net/general/20240826/1724641471286750a7ebab13f4fbc9f5a578da619ab50.jpg
[S4]: https://www.realme.com/in/support/kw/doc/2241809
[S5]: https://www.realme.com/in/support/kw/doc/2059699
[S6]: https://play.google.com/store/apps/details?id=com.realme.link&hl=en_IN
[S7]: https://play.google.com/store/apps/details?id=com.heytap.headset&hl=en_IN
[S8]: https://beebom.com/realme-techlife-studio-h1-review/
[S9]: https://www.giznext.com/news/realme-techlife-studio-h1-review-a-new-challenger-in-the-headphone-market/
[S10]: https://www.geekman.in/best-wireless-headphones-under-5000-india/#realme-techlife-studio-h1
[S11]: https://c.realme.com/in/api/thread/1966630908740059136
[S12]: https://c.realme.com/in/api/thread/2030468947068887040
[S13]: https://github.com/madhuraj0/h1-control
[S14]: https://github.com/madhuraj0/h1-control/releases/tag/v0.1.0
[S15]: https://github.com/madhuraj0/h1-control/blob/a7d08b045164996c7b5153660eb7d640898b3242/core/src/commonMain/kotlin/dev/madhuraj/h1/core/HeadsetSession.kt
[S16]: https://github.com/madhuraj0/h1-control/blob/a7d08b045164996c7b5153660eb7d640898b3242/core/src/commonMain/kotlin/dev/madhuraj/h1/core/Rcsp.kt
[S17]: https://github.com/madhuraj0/h1-control/blob/a7d08b045164996c7b5153660eb7d640898b3242/core/src/commonTest/kotlin/dev/madhuraj/h1/core/HeadsetSessionTest.kt
[S18]: https://github.com/madhuraj0/h1-control/blob/a7d08b045164996c7b5153660eb7d640898b3242/androidApp/src/main/kotlin/dev/madhuraj/h1/HeadsetController.kt
[S19]: https://github.com/Akshat-hub7/Realme_Headphone_Studio_H1_custom_app/blob/5dc85164874ef0145bb84a0588704c409ed99173/main.js
[S20]: https://pittvandewitt.github.io/Wavelet/Features/
[S21]: https://pittvandewitt.github.io/Wavelet/Troubleshooting/
[S22]: https://play.google.com/store/apps/details?id=com.maxmpz.equalizer&hl=en_IN
[S23]: https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results/README.md
[S24]: https://gadgetbridge.org/gadgets/headphones/realme/
[S25]: https://sourceforge.net/projects/equalizerapo/
[S26]: https://sourceforge.net/projects/peace-equalizer-apo-extension/
[S27]: https://www.bluetoothgoodies.com/a2dp/
[S28]: https://m.media-amazon.com/images/I/A1Nbx-mSV4L.pdf
[S29]: https://fccid.io/2AUYFRMA2411/Test-Report/SZ25010255W01-BLE-DTS-8096694
[S30]: https://fccid.io/2AUYFRMA2411/Internal-Photos/Internal-Photos-8096681
[S31]: https://learn.microsoft.com/en-us/windows/win32/api/bluetoothapis/nf-bluetoothapis-bluetoothenumerateinstalledservices
[S32]: https://learn.microsoft.com/en-us/windows/win32/bluetooth/bluetooth-and-connect
[S33]: https://fccid.io/2AUYFRMA2411/User-Manual/User-Manual-V2-8100920
[S34]: https://developers.google.com/nearby/fast-pair/specifications/extensions/messagestream
[S35]: https://developers.google.com/nearby/fast-pair/specifications/extensions/hearablecontrols
[S36]: https://developers.google.com/nearby/fast-pair/specifications/extensions/mac
[S37]: https://github.com/Jieli-Tech/Android-JL_Bluetooth
[S38]: https://developer.android.com/develop/connectivity/bluetooth/bt-permissions
