# H1 Control attribution

The status framing, attribute interpretation and upstream captured test fixture
used by `rcsp_status.py`, `h1_status_probe.py` and `test_rcsp_status.py` are based on
[H1 Control](https://github.com/madhuraj0/h1-control), by Madhuraj, pinned to
commit `a7d08b045164996c7b5153660eb7d640898b3242`.

Relevant upstream sources:

- [Rcsp.kt](https://github.com/madhuraj0/h1-control/blob/a7d08b045164996c7b5153660eb7d640898b3242/core/src/commonMain/kotlin/dev/madhuraj/h1/core/Rcsp.kt)
- [HeadsetSession.kt](https://github.com/madhuraj0/h1-control/blob/a7d08b045164996c7b5153660eb7d640898b3242/core/src/commonMain/kotlin/dev/madhuraj/h1/core/HeadsetSession.kt)
- [FrameParser.kt](https://github.com/madhuraj0/h1-control/blob/a7d08b045164996c7b5153660eb7d640898b3242/core/src/commonMain/kotlin/dev/madhuraj/h1/core/FrameParser.kt)
- [HeadsetSessionTest.kt](https://github.com/madhuraj0/h1-control/blob/a7d08b045164996c7b5153660eb7d640898b3242/core/src/commonTest/kotlin/dev/madhuraj/h1/core/HeadsetSessionTest.kt)
- [Android SPP transport](https://github.com/madhuraj0/h1-control/blob/a7d08b045164996c7b5153660eb7d640898b3242/androidApp/src/main/kotlin/dev/madhuraj/h1/HeadsetController.kt)

Adaptations here add Windows native transport, a single-query allowlist,
timeouts, stricter parsing and redacted observations. The upstream author labels
the fixture as a real H1 firmware 2.4.0 capture; that provenance is an author
claim, not an independent local hardware observation.

The `android-app/` Kotlin scaffold additionally reuses adapted copies of
`Rcsp.kt`, `FrameParser.kt`, `HeadsetSession.kt` and the session test under
`android-app/app/src/main/java/.../h1core` and
`android-app/app/src/test/java/.../h1core`. Each copied file carries its MIT
attribution header. The app adaptation renames the package and, importantly,
changes `setNoiseMode` so a requested mode stays **pending** until the headset
reports it; the upstream version updated the UI optimistically before
confirmation. No Android RFCOMM transport code was copied, and no headset setter
beyond the upstream-documented noise-mode command is claimed.

On 2026-11-25 the owner asked to extend the full H1 Control app rather than a
separate scaffold. A local MIT-compliant fork was built from the same pinned
commit with LDAC quality controls, companion-association onboarding and a
side-by-side `dev.madhuraj.h1.fork` application id. Upstream files and the MIT
notice remain in the retained source tree; exact changes and build identity are
in [h1-control-fork-evidence.json](h1-control-fork-evidence.json).

## Upstream MIT license

MIT License

Copyright (c) 2026 Madhuraj

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
