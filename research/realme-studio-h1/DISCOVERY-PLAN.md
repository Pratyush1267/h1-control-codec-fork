# Studio H1: evidence-first companion-app plan

Prepared and updated 2026-11-24. A Windows status utility is now implemented
and live-tested; a full companion app or setting-changing driver was not built here.
Public-source research was followed by cache inspection and one authorised
SPP status request. No setters or BLE operations were executed by the agent.
The owner subsequently reported ANC and ambient/transparency working, additional
feature detection, and Android's Best Effort LDAC quality default. The fuller
Android app target is specified in [APP-SCOPE.md](APP-SCOPE.md).

## Goal and current evidence

Determine whether a genuine companion app can read and change settings on the
Realme TechLife Studio H1, rather than merely changing audio on its host device.

The manufacturer identifies the product as **RMA2411** and lists
`HFP/AVRCP/A2DP/BLE/SPP`. This makes BLE and Bluetooth serial worth investigating.
It does **not** establish accessible GATT services, an RFCOMM control endpoint,
or command semantics. See the [official specification image](https://image01.realme.net/general/20240826/1724641472845b7951f5e8f7c4b3ab95b04165d33e20d.jpg)
and [official-evidence.json](official-evidence.json).

The manufacturer documents local buttons for ANC/awareness, gaming mode, LDAC,
spatial effects, and adaptive noise mode in its
[button chart](https://image01.realme.net/general/20240826/1724641471286750a7ebab13f4fbc9f5a578da619ab50.jpg).
Local control does not imply Bluetooth remote control.

### Windows evidence already obtained

The owner confirmed that the headset is connected to the Windows machine
running the agent. [windows-cache-observation.json](windows-cache-observation.json)
records that Windows reports the selected exact-name headset connected,
remembered and authenticated for ordinary Bluetooth pairing.

The installed/enabled-service inventory includes **Serial Port (SPP)**
`00001101-0000-1000-8000-00805f9b34fb` and **Google Fast Pair Message Stream**
`df21fe2c-2515-4fdb-8886-f12c4d67927c`. These are observed Windows service records,
not merely brochure declarations. They do not establish a complete fresh SDP
database, RFCOMM channel, command semantics or Fast Pair account-key access.

This observation used [windows_probe.py](windows_probe.py), which requests no
Bluetooth inquiry and opens no socket. Device addresses are redacted. Its
scope is documented by Microsoft's
[installed-service enumeration API](https://learn.microsoft.com/en-us/windows/win32/api/bluetoothapis/nf-bluetoothapis-bluetoothenumerateinstalledservices).

### Model-specific protocol now corroborated

The examined [H1 Control release](https://github.com/madhuraj0/h1-control/releases/tag/v0.1.0)
provides Android/macOS apps and an MIT-licensed Kotlin protocol core. Pinned
source and limitations are recorded in [h1-control-evidence.json](h1-control-evidence.json).

On the owner's unit, [h1_status_probe.py](h1_status_probe.py) sent exactly one
source-backed 14-byte status request and received a valid 96-byte response:
**39% battery**, reported mode **off**, and modes **off/ANC/transparency**.
It then closed the connection. See [windows-status-observation.json](windows-status-observation.json)
and [windows-status-verification.json](windows-status-verification.json).

This is sufficient to proceed with a status app and evaluate the existing
controller. It is not a local setter test, firmware identification or proof of
headset-stored music EQ. The numeric RFCOMM channel remains locally unverified.

### Owner-reported functional confirmation

The owner reports working ANC and ambient/transparency, plus detection of game
mode, spatial audio, LDAC, adaptive noise and the active codec.
[owner-control-validation.json](owner-control-validation.json) retains the exact
messages. Additional detection is not an additional remote setter test. The
actual app/tool, platform, versions, status freshness, Off test and repeat/reversal
details were not explicitly supplied. Keep this separate
from the agent's Windows status capture; it is not a Windows setter measurement.

The pinned README says adaptive-noise state is not reported, so reconcile the
owner's adaptive-detection report with the actual app build/display before
implementing a state source. Android LDAC playback quality is a separate
phone-side module; see [android-codec-evidence.json](android-codec-evidence.json)
for the restricted API and companion-association boundaries.

## Stage 0 — identify the actual unit and requirements

Confirmed by the owner in this session: **Android** is the target platform and
the headset is **available now**. Still required before claiming full control support:

- Model label and paper manual, preferably photos.
- Host device, OS version, region, and official companion-app version if installed.
- Desired features ranked: ANC, transparency, EQ, battery, spatial audio, gaming,
  codec information, multipoint management, firmware updates.
- Current known-good pairing/audio behavior and initial mode, to preserve it.
- Firmware version only if a legitimate interface actually reports it.

**Gate:** do not claim model-specific support from the Bluetooth display name
alone. Do not infer hardware revisions or a chipset from branding or LDAC.

## Stage 1 — verify existing support on the owner's setup

1. Use legitimate app-store listings for any official app being tested.
2. Check the current device catalogue in the owner's region and app version.
3. Record whether the headset is merely visible as a Bluetooth audio device or
   has an actual model-specific settings page.
4. If a settings page exists, observe one reversible action, with consent,
   and verify the headset itself changes mode. Record app/OS/firmware versions.
5. If not supported, record the exact failure and search boundary. One failed
   scan does not establish absence of all control interfaces.

Do not install unreviewed modified APKs, change regions/accounts speculatively,
reset the headset, or erase a known-good pairing to make a test pass. The
examined open-source H1 Control release is a separate, explicitly unofficial
candidate; no release binary was installed by the agent during this investigation.

**Output:** versioned compatibility observation, not a universal yes/no claim.

## Stage 2 — discovery before control

Implemented first deliverables: the **Windows cache probe and one-shot status
utility**, because the headset was already connected to this machine. Android
remains the owner's eventual target. Prefer evaluating or selectively extending
the existing H1 Control Android app over rebuilding discovery from scratch.
The rationale and API references are in [REPORT.md](REPORT.md).

Initial capabilities:

- User-initiated, bounded discovery of the selected owned device.
- Paired-device metadata and cached versus freshly discovered Classic service
  UUIDs clearly distinguished.
- BLE advertising inventory, then optional user-initiated GATT service and
  characteristic-property enumeration.
- Known standard read-only information only if actually exposed and authorized.
- Local export of a redacted service inventory and diagnostics.
- A button reference guide based on the official instructions.

The Windows CLI and offline parser/safety/provenance tests are implemented.
One owner-authorised live query verified status reading. No Android application
was built locally and no setter was executed by the agent. The subsequent owner
ANC/ambient confirmation is a separate functional report.

Initial exclusions:

- No unreviewed characteristic operations, setting-changing writes, arbitrary
  RFCOMM payloads, automatic notification subscriptions, fuzzing, or firmware updates.
  The one source-backed status query was separately authorised and recorded.
- No fabricated ANC/EQ sliders implying confirmed headset control.
- No hidden-API reflection, root dependency, background tracking, or cloud upload.
- No automatic bonding changes or connections to nearby unrelated devices.

“Read-only” describes the absence of setting-changing commands. Active scanning,
SDP/GATT discovery and connection establishment still create traffic and can
affect connection state. Keep tests user initiated and check audio/multipoint
behavior before and after each connection.

**Output:** actual services/properties and explicit unavailable/unknown states.
BLE functionality remains unmeasured. SPP status reading is agent-verified;
ANC/ambient functions are owner-reported working, without a captured/versioned
setter test. A service UUID alone would not have established either result.

## Stage 3 — capture and inspect one hypothesis at a time

With explicit owner consent, use Android Bluetooth HCI logging or a suitable
capture from the owned host. Keep the original file private and record its
SHA-256. Full bug reports and pairing captures can contain sensitive data.

Suggested experiment:

1. Record a baseline connection and initial audible/visible headset mode.
2. Change **only ANC/awareness mode** with the documented hardware button.
3. Record action timestamps and actual voice prompts/mode changes.
4. Repeat the same sequence three times, then restore the starting mode.
5. Compare the captured traffic with the baseline before testing another feature.

Button presses can change headset-local state without transmitting vendor
messages. No correlated packets in this experiment means **no packets observed
under these conditions**, not “the firmware has no remote interface.”

If the owner supplies a legitimately obtained relevant APK, REA can inspect its
model catalogue, transport classes, resources and native libraries. Bind the
explicit artifact, record its hash/provenance, and follow references rather than
matching a model string alone. Static code is not runtime compatibility proof.
Do not install or reconfigure analysis tools simply to study the marketing page.

Protocol ledger for every candidate command:

| Field | Required evidence |
|---|---|
| Device/build identity | Actual model and reported firmware, if available |
| Transport | Observed BLE/GATT, RFCOMM/SPP, or other documented path |
| Endpoint | Observed UUID/channel/characteristic, not a guessed value |
| Message | Exact bytes, direction, timestamp and capture frame |
| Semantics | Observed feature and before/after state |
| Reply | Actual acknowledgement/query result, or explicitly unknown |
| Reversal | Known, owner-approved path back to the original mode |
| Confidence | Observed, inferred, or unresolved |

**Output:** an evidence-linked ledger and private raw captures. A control
endpoint can remain unresolved even after successful transport discovery.

## Stage 4 — build a model-specific driver only after proof

Proposed architecture:

```text
Native UI
  -> capability/state model (unsupported and unknown remain explicit)
  -> read-only discovery layer
  -> transport adapter (observed GATT or RFCOMM)
  -> optional RMA2411 protocol driver
  -> observed, allowlisted commands only
```

Setting-changing operations start disabled. The implemented status parser was
tested offline from upstream fixtures and then corroborated by one owner-unit
response. Synthetic robustness tests do not establish setting compatibility.
Reuse the upstream MIT core with attribution; for any fork, replace optimistic
state updates with matched acknowledgement/push and timeout/error states.

Control acceptance criteria:

- A supported command's meaning and reversal are grounded in evidence.
- The owner approves the specific reversible hardware test.
- Three repeat trials change the actual headset and verify the expected state.
- The app reports observed acknowledgement/state, not an optimistic UI toggle.
- Reconnect/restart behavior is tested without damaging baseline pairing/audio.
- Unknown firmware revisions stay limited to diagnostics until their setters
  are explicitly validated; do not infer firmware from a matching packet layout.

Firmware flashing, bootloader access and unknown commands are outside this plan.

## Agentic engineering with explicit gates

Use separate work units only when the owner proceeds with the build:

| Work unit | Deliverable | Completion gate |
|---|---|---|
| Evidence investigator | Sources, artifact hashes, model/service ledger | Claims trace to evidence; unknowns retained |
| Protocol engineer | Offline parser and captured fixtures | No invented UUIDs/opcodes; parser tests pass |
| App engineer | Permissions, discovery UI, transport lifecycle | No control writes in the discovery build |
| Hardware tester | Owner-run before/after trials | Real mode changes independently verified |
| Privacy/safety reviewer | Redaction, consent and recovery review | No secrets/cloud uploads; known-good state preserved |

A pretty interface, successful BLE connection, or passing mocked tests is not
proof of a working Studio H1 companion app.

## Go / no-go decision

- **Go: status MVP.** A source-backed status read is verified on the owner's Windows pairing.
- **Go: reuse working noise control.** H1 Control is examined and model-specific;
  the owner now reports ANC and ambient functions working, with test details unspecified.
- **Go: fuller status/audio-tools scope.** Include supported codec information
  and a quality Settings helper; additional detection needs source/freshness labels.
- **Conditional go: direct LDAC quality/persistence.** Validate API access and
  actual configuration on the owner's Android/OEM build; no root requirement
  or unrestricted normal-app access is assumed.
- **Conditional go: custom/distributed controls.** Collect the actual test app,
  platform and versions plus repeat/reversal/reconnect evidence before broader claims.
- **Fallback:** status/guide utility plus clearly labelled host-side EQ.
- **Stop for unverified features:** EQ, firmware, game/spatial/LDAC/adaptive
  setters have no verified owner-unit implementation. A full OEM-equivalent app
  does not follow from the successful status read.

## Smallest reversible next step

The owner wants more than noise control. Obtain phone model, Android/OEM version,
actual app/version and a redacted adaptive-state screenshot, then implement the
standard foundation in [APP-SCOPE.md](APP-SCOPE.md). Preserve the original status
measurement and working pairing. Direct codec-quality persistence and additional
headset setters require their own reversible, explicitly approved tests.
Do not infer EQ, other setters or firmware support from detection. Separately
check realme Link if official support needs resolution.
