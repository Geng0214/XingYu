# QuickJS source pin

Unmodified engine files from the official QuickJS 2026-06-04 release at https://bellard.org/quickjs/quickjs-2026-06-04.tar.xz . Archive SHA-256: b376e839b322978313d929fd20663b11ba58b75df5a46c126dd19ea2fa70ad2a.

Only the engine, required headers, VERSION and LICENSE are vendored. The CLI, std/os library, compiler, examples, tests and dynamic native module loader are not part of the Android library. There is no network fetch during an Android build. source-lock.json records every original file hash; Gradle verifies them before compilation. Git preserves their original bytes.

The app's JNI bridge is in app/src/main/cpp. It is adapted from the Operit handoff baseline's independent runtime design, with a per-invocation memory limit, stack limit, job limit, deadline, cancellation flag and byte-array UTF-8 boundary. This is a restricted language runtime, not an Android process sandbox. No plugin host APIs are exposed by this checkpoint.

The corresponding official Windows CLI is cached separately by ci/script/prepare_quickjs_test.py for host adapter tests. It is not this JNI binary and does not validate Android runtime behavior. Do not bundle that executable or its DLL in the APK or the source archive.
