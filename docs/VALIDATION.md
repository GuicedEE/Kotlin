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

## Mutiny and database integration — 2026-11-15

The expanded six-project Kotlin reactor passes `mvnw.cmd -B -ntp install` with 10 core
runtime tests and 5 optional Mutiny tests. The latter cover lazy subscription, cross-thread
emission resuming on the owning context, call scope, failure/null semantics, cancellation,
shutdown, Multi/Flow conversion and Deferred-to-Uni conversion.

`python scripts/verify-mutiny-wrapper.py` confirms all 16 supported upstream coroutine
class/metadata entries match Mutiny Kotlin 3.3.1 byte-for-byte. Split-package convenience
classes and their dependent `MultiBuilders` classes are absent; the wrapper has an explicit
`mutiny.kotlin` module descriptor.

The Examples repository now contains `Kotlin/RestClient` and `Kotlin/Persistence`, each
with Moditect and Maven jlink packaging. The complete seven-project Kotlin examples reactor
passes `verify`, including three integration tests. Persistence uses PostgreSQL 17 with
SCRAM authentication in a disposable Testcontainers database. It checks concurrent HTTP
inserts/reads, failure rollback, explicit cancellation rollback and lifecycle shutdown with a
flushed transaction in progress. An independent JDBC connection observes only the two
successful writes after the application has stopped.

Both new applications pass module-path validation and execute through both generated
launchers. The verification script checks committed rows using PostgreSQL's own client
after each persistence run. It also patches only the shutdown priority in a temporary
runtime JAR copy: the former priority fails with the expected cleanup-order assertion,
while the new priority passes. Installed artifacts and source are untouched by this check.

These runs exposed two Hibernate wrapper issues, fixed in the Services repository:
identity generation needs a qualified export of `org.hibernate.generator.values.internal`
to Hibernate Reactive, and missing optional mapping resources in `jrt:` images must be
distinguished from real I/O errors. All eight `ExplicitPersistenceScannerTest` tests pass,
including missing/present runtime-image resources. The linked PostgreSQL application
also exercises both fixes without JVM access flags.

Reproduce the packaged integration checks from `GuicedEE/examples/Kotlin` using
`python scripts/verify-integrations.py --check-shutdown-regression`. A custom Moditect
destination is selected with `--moditect-directory`. Results are saved under that project's
`target/integration-verification-*`. The original Basic example's module-path and both
launcher checks also pass after these changes.

This remains local Windows/JDK 25 validation with locally installed GuicedEE 2.6.0 artifacts;
no Central publication or hosted-CI result is implied.
