# Initial validation — 2026-11-15

Validated on Windows with JDK 25, Maven wrapper 4.0.0-rc-7 and the locally installed
GuicedEE 2.6.0 baseline. Platform 2.6.0 was not available from Maven Central at validation time.
This is local integration evidence, not a release-publication or hosted-CI result.

| Check | Result |
| --- | --- |
| `mvnw.cmd -B -ntp verify` | Passed; all four reactor projects |
| JUnit runtime tests | 10 passed, 0 failures/errors/skips |
| Java module-path validation | Passed |
| Actual GuicedEE application smoke | Passed: concurrent HTTP requests with scoped identity checks, event-bus reply, Kotlin JSON, service discovery |
| Lifecycle cancellation | Passed: application shutdown waits for suspended cleanup while the call scope remains active |
| Full application `jlink` | Passed using the generated runtime dependency directory and application JAR |
| Linked application smoke | Passed with the same HTTP/event-bus/lifecycle assertions |
| Wrapper preservation | All 35 upstream class/Kotlin metadata entries unchanged |
| Packaged source archive | Contains Kotlin and Java implementation sources |

Reproduce the runtime checks with `python scripts/verify-runtime.py --jlink`. Each run writes
logs and `results.json` under a new `target/runtime-verification-*` directory.

The creator skill's local-file baseline checker was also run. It confirms separate main/test
modules and packages, test naming, and dual service registration. Its remaining findings need
context: the Maven wrapper, Java baseline, imported BOM, and JUnit 6.1.3 version live in the
root/inherited Maven model; the bootstrap main belongs to the example, not the runtime library.
The lifecycle package uses Jackson's public registration API and is not a deserialized DTO;
it needs Guice access, which is explicitly opened. Kotlin DTO packages are opened to Jackson
and Kotlin reflection. These decisions were verified through compilation and execution.

Direct annotated suspending endpoint discovery, coroutine-aware service interception, and
automatic HTTP-disconnect cancellation are outside this initial integration. The Future boundary
and explicit `CoroutineCall.cancel()` behavior are documented in the README.
