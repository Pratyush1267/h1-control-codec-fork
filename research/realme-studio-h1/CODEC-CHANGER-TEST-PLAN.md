# Bluetooth Codec Changer: approved analysis and phone-test plan

Prepared 2026-11-24. **Plan and artifact acquisition, not a completed phone test.**

## Target and question

- App: [Bluetooth Codec Changer](https://play.google.com/store/apps/details?id=com.amrg.bluetooth_codec_converter&hl=en), AmrG DEV.
- Selected package: `com.amrg.bluetooth_codec_converter`.
- APKPure's public download supplied a file labelled **2.6.1**, 13,377,290 bytes.
- Exact APK SHA-256: `83963fcf8e18dfa44ccd9398018574e7bc4abc15e0ab6c91c1c9b37fa63d60c1`.
- Question: how does the unmodified app change Android A2DP codec/LDAC quality
  without receiving root or ADB-granted privileges, and is that route usable for
  the owner's fuller H1 companion app?

The APK, Play listing, pinned publisher privacy policy and acquisition receipt
are retained privately under the approved local temporary directory. Do not
redistribute the proprietary APK or a complete decompilation in this repository.

REA signing-structure Evidence
`ev_c92b7029602718fc7e42db61da01b414deb02236befeeedda9256e33a2b20ee6`
reports v2/v3 signing records with the same declared certificate SHA-256
`31332a623837cc09eef5672c00a3f9242d012ac11bc8baf84b8a0ee8b0b3f5de`
and self-consistent content digests. **REA did not cryptographically verify the
signatures, authenticate the publisher, or execute Android code.** A cryptographic
verification and actual package/manifest inspection remain pre-install gates.

## Owner-approved scope

The owner chose **their phone**, not an emulator, and approved portable analysis
tools plus REA MCP configuration. Tool acquisition stays under
the approved local temporary directory. No global PATH, Java installation,
Windows driver change, security-policy weakening, phone root, or headset re-pairing
is authorized by this setup.

The reviewed MCP plan changes only
`C:\Users\praty\.config\opencode\opencode.json`, preserves unrelated settings,
and creates `opencode.json.rea.backup` for the inspected configuration. The portable
JDK/JADX paths must be scoped to the REA process. No skill replacement was planned.
Follow OpenCode V2's connection/reload behaviour; do not restart a healthy service
just to apply a configuration edit.

## Evidence stages and gates

1. **Static REA inspection.** Verify APK integrity with Android's `apksigner`,
   establish package/version/SDK/debuggable identity, and follow the actual
   companion-association and codec-setting methods. Raw string matches are
   candidates, not a demonstrated call path. Inspect LDAC quality-value mapping
   and any separate privileged diagnostics without conflating them with the
   basic codec setter.
2. **Select the phone.** The owner connects USB and approves debugging on the
   phone. Require one explicitly selected authorized Android device; record
   model, Android/OEM build and app version. Do not infer a USB target from a
   Windows wireless-device entry. If the app is already installed, preserve it:
   do not overwrite, downgrade, clear data or uninstall it to match our artifact.
   Prefer the installed unmodified Play app and retain its own artifact identity.
3. **Install/launch only the selected app.** If absent, finish the pre-install
   gates before installing the owner-approved unmodified APK. Resolve the exact
   launcher activity instead of random input or broad app automation. No premium
   purchase, licensing bypass, modified/re-signed APK, or automatic permission
   grant. The owner handles normal Bluetooth and companion-consent prompts.
4. **Observe before changing quality.** Retain narrowly relevant package
   permissions, companion association and A2DP state before/after onboarding.
   ADB is our observation transport; do not grant the app `BLUETOOTH_PRIVILEGED`,
   `WRITE_SECURE_SETTINGS`, root, Shizuku or shell-identity access. Do not enable
   Bluetooth HCI logging or capture a whole bug report without separate approval.
5. **One reversible quality test, separately approved.** Only with the H1 already
   using LDAC and the true starting policy established, propose one transition
   from that policy to Optimized for Audio Quality, then return to the starting
   policy. Record the app request, resulting system configuration, timestamps and
   owner-observed audio continuity. A displayed policy is not measured radio
   bitrate. No automatic reconnect reapplication or repeated setter loop in this
   first trial.

**Read-only precondition check (completed).** The first authorized phone check
found the H1 already active on LDAC with `codecSpecific1=1000` and the stack
reporting HIGH / 990 kbps — already the Optimized-for-Audio-Quality target. No
setter was called. A brief 1000 -> 1001 -> 1000 history was observed in the
read-only dump, with its cause unattributed. See
[the redacted quality observation](codec-quality-observation.json). Because the
target is already active, the originally proposed transition is moot; a
controlled round-trip is a separate, explicitly optional decision.

**Completed controlled round-trip.** After the owner updated the app from 2.4.1
to 2.6.1, the owner used its normal UI to change the policy 1000 -> 1001 and
back to 1000, with read-only ADB verification of HIGH/990 -> MID/660 ->
HIGH/990 on the active LDAC link. See
[the round-trip evidence](codec-quality-roundtrip.json). The earlier 2.4.1 UI
did not visibly apply the change in this session; no cause was established.
A temporary ADB-shell reflection runner was also tried, but its config was
rejected by the service and changed nothing; its pushed files were removed.
The owner's updated 2.6.1 installation was pulled read-only through ADB and
decompiled: same reflection route, same quality values, and a signer matching
the retained 2.6.1 artifact. See
[installed 2.6.1 evidence](installed-261-static-evidence.json). No root,
re-signing, privileged grant or re-pairing was used. Our own app still needs its
own user-approved association; the design is in
[COMPANION-ONBOARDING.md](COMPANION-ONBOARDING.md).

## What "run through REA" means here

REA's Android/JADX tools inspect the APK **statically** and never launch it.
Android executes the app on the phone. REA's separate Frida tools can instrument a
runtime only when compatible bindings and legitimate target-process access exist.
An unrooted phone with a non-debuggable app does not become Frida-attachable merely
because USB debugging is enabled. Do not root the phone, bypass its protections,
or re-sign the APK to manufacture that access.

If Frida access is unavailable, use REA's source-level evidence plus ordinary ADB
state/log observations of the **unmodified** app, and label that combination
accurately rather than claiming REA captured Java calls. A rooted analysis
emulator would be a separate experiment and would not, by itself, prove actual
H1 codec behaviour or the absence of elevated app permissions on the owner's phone.

## Stop conditions

- No authorized phone, multiple ambiguous devices, or an unresolved driver requirement.
- Package/version/signer mismatch or a platform installation/security warning.
- A requirement to replace existing app data, grant elevated app permissions,
  reset/re-pair the H1, change another Bluetooth device, or bypass a protection.
- Unknown starting quality, no LDAC negotiation, inability to reverse, or an
  unexpected connection/audio failure.

Keep device addresses, USB serials, complete Bluetooth dumps and raw logs private.
Published findings must separate publisher claims, static implementation,
owner reports and agent-observed phone behaviour.
