# Studio H1: observations from your Windows pairing

Observed 2026-11-24. Sources: [initial cache](windows-cache-observation.json),
[live status query](windows-status-observation.json),
[offline byte verification](windows-status-verification.json), and
[cache after the query](windows-cache-after-status.json).

## What is confirmed

Windows reports a Bluetooth device named **realme TechLife Studio H1** as
connected, remembered and authenticated for normal Bluetooth pairing. Its
installed/enabled-service inventory contains seven UUIDs, including:

| Observed service | UUID | What this establishes |
|---|---|---|
| Serial Port (SPP) | `00001101-0000-1000-8000-00805f9b34fb` | Windows has a serial-profile service record for this pairing |
| Google Fast Pair Message Stream | `df21fe2c-2515-4fdb-8886-f12c4d67927c` | Windows has the UUID documented for that message-stream service |
| Audio Sink (A2DP) | `0000110b-0000-1000-8000-00805f9b34fb` | Audio service record |
| A/V Remote Control Target | `0000110c-0000-1000-8000-00805f9b34fb` | Remote-media service record |
| A/V Remote Control | `0000110e-0000-1000-8000-00805f9b34fb` | Remote-media service record |
| Hands-Free (HFP) | `0000111e-0000-1000-8000-00805f9b34fb` | Hands-free service record |

The remaining observed UUID is `00001124-0000-1000-8000-00805f9b34fb`; the probe
leaves it unclassified rather than treating it as a control interface.

The Fast Pair UUID is documented in Google's
[Message Stream specification](https://developers.google.com/nearby/fast-pair/specifications/extensions/messagestream).
The inventory's scope is Microsoft's
[BluetoothEnumerateInstalledServices](https://learn.microsoft.com/en-us/windows/win32/api/bluetoothapis/nf-bluetoothapis-bluetoothenumerateinstalledservices):
services enabled on the device's Windows pairing, **not fresh remote SDP**.

## Live protocol result

With the owner's authorisation, [h1_status_probe.py](h1_status_probe.py) opened
one SPP/RFCOMM connection and sent the exact status request from
[H1 Control's pinned tests](https://github.com/madhuraj0/h1-control/blob/a7d08b045164996c7b5153660eb7d640898b3242/core/src/commonTest/kotlin/dev/madhuraj/h1/core/HeadsetSessionTest.kt):

```text
FE DC BA C0 07 00 06 01 FF FF FF FF FF EF
```

The headset returned **96 bytes**, decoded as one RCSP `0x07` response with
**sequence 1**, **status 0**, battery **39%**, reported noise mode **off**, and
the modes **off / ANC / transparency**. No skipped bytes, incomplete frame or
attribute-format warnings were recorded. The connection closed after the reply.

This establishes a working source-backed **status protocol on the owner's
connected unit**, beyond merely seeing service UUIDs. It does not establish
successful mode-changing commands. The noise-mode value is the protocol's
report; H1 Control warns it may be stale after physical ANC-button changes.

Windows resolved the connection using the SPP UUID and port zero, following
[Microsoft's documented mechanism](https://learn.microsoft.com/en-us/windows/win32/bluetooth/bluetooth-and-connect).
The probe did **not** verify the resolved channel number or a full SDP database.
H1 Control's author-reported channel 1 must not be labelled a local observation.

The later cache check still reports the headset connected, remembered and
authenticated, with the same seven service UUIDs. Audio continuity itself was
not measured.

## What remains unverified

- Printed model RMA2411 and the actual firmware version.
- Fresh SDP attributes, service names and RFCOMM channels.
- BLE advertisements and accessible GATT characteristics.
- Packet-level setter behaviour and Windows setter execution; status reading is
  agent-verified and the owner's ANC/ambient feedback is recorded separately below.
- Whether this headset implements optional Fast Pair ANC controls.
- Authentication requirements of untested features; the one status query succeeded
  without an app-level authentication handshake.
- EQ, gaming, spatial, LDAC/adaptive or firmware commands; no new feature support
  is implied by detection of a corresponding state.

## Subsequent owner confirmation (not a Windows measurement)

The owner reports ANC and ambient/transparency functions working in a follow-up
message, then adds detection of game mode, spatial audio, LDAC, adaptive noise
and the codec in use. [owner-control-validation.json](owner-control-validation.json)
retains both reports and the Android Best Effort LDAC-quality observation. The
actual app/tool, test versions and status sources/freshness were not restated.
This strengthens functional feasibility without changing the original Windows
observation: the agent sent one status request and no setter. No new Bluetooth
operation was performed to record the feedback.

The fuller Android scope and separate phone-side codec-quality limits are in
[APP-SCOPE.md](APP-SCOPE.md). No Android quality setter or new headset feature
was tested on Windows. In particular, a negotiated LDAC codec is not proof of
the configured quality policy or instantaneous bitrate.

Windows' ordinary authenticated pairing is **not** evidence that a third-party
app possesses the Fast Pair account key required by authenticated setters. See
[Hearable Controls](https://developers.google.com/nearby/fast-pair/specifications/extensions/hearablecontrols)
and [Message Authentication Code](https://developers.google.com/nearby/fast-pair/specifications/extensions/mac).

## Safety boundary

The initial and later cache inspections opened no socket and sent no vendor
commands. The separate live query sent **one 14-byte GET_SYS_INFO request**.

No setting-changing command, automatic ACK, retry, Bluetooth inquiry, GATT
operation, pairing change, service enablement, firmware operation or physical
mode change was requested. Windows' UUID resolution may itself use SDP traffic;
this is not passive metadata-only discovery. Device addresses are redacted.

## Reproduce the safe cache inspection

From `C:\Users\praty\Downloads\opcore`:

```powershell
python "research/realme-studio-h1/windows_probe.py"
```

To save a **new** redacted observation, choose a filename that does not exist:

```powershell
python "research/realme-studio-h1/windows_probe.py" --output "research/realme-studio-h1/windows-cache-observation-02.json"
```

The utility refuses to overwrite existing files or choose among multiple
exact-name matches. It requires Windows and Python's standard library only.

Offline tests, which do not invoke Bluetooth APIs:

```powershell
python -m unittest discover -s "research/realme-studio-h1" -p "test_*.py" -v
```

The offline suite covers the cache probe, bounded parser, one-query transport
boundary and retained evidence. Both the cache probe and the live status query
ran successfully. Offline tests are not setting-control tests.

## Run the status utility

Default operation is an **offline plan**, with no Bluetooth API calls:

```powershell
python "research/realme-studio-h1/h1_status_probe.py"
```

An explicitly authorised future query requires both new output filenames:

```powershell
python "research/realme-studio-h1/h1_status_probe.py" --execute-status-query --output "research/realme-studio-h1/windows-status-observation-02.json" --private-capture "$env:LOCALAPPDATA\Temp\opencode\realme-h1-private-status-02.json"
```

That command **sends vendor bytes** and opens a control connection. It is not
necessary to repeat the successful query to use the saved findings. The utility
has no setter or arbitrary-payload argument and refuses existing output files.

Original raw bytes stay in the approved private temporary directory. The exact
capture-file SHA-256 is
`5dd8c4f925a5f972780f7e579ab52598e3708cd8d538e7b7a0edb151a7bf8a20`.
[windows-status-verification.json](windows-status-verification.json) explains
that the first observation's hash covered LF-normalised JSON before Windows
wrote CRLF. Both originals remain unchanged; the writer was corrected and
regression-tested offline without repeating the live query.

## Next evidence gate

ANC and ambient now have an owner functional confirmation. If extending or
porting the examined [H1 Control implementation](https://github.com/madhuraj0/h1-control/releases/tag/v0.1.0),
record the actual app/tool and versions, Off-mode behaviour, repeat/reversal and
reconnect results. The Windows probe is not holding a control connection.
A versioned realme Link catalogue/pairing check remains a separate way to resolve
official support. No extra hardware test is needed merely to preserve the report.

Do not infer remote EQ, gaming, spatial, LDAC or firmware control from the
successful status response. See [REPORT.md](REPORT.md) for the complete synthesis.
