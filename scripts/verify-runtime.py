#!/usr/bin/env python3
"""Validate and run the actual application module graph, optionally from a jlink image."""
import argparse
import datetime
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--jlink", action="store_true", help="Also link and execute the complete application")
args = parser.parse_args()
stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%d-%H%M%S")
output = root / "target" / ("runtime-verification-" + stamp)
output.mkdir(parents=True)
java = shutil.which("java")
if not java:
    raise SystemExit("JDK 25+ java must be on PATH")
artifact = root / "examples/basic/target/kotlin-example-2.6.0.jar"
dependencies = root / "examples/basic/target/modules"
if not artifact.exists() or not dependencies.is_dir():
    raise SystemExit("Build first: ./mvnw -B verify")
module_path = os.pathsep.join((str(artifact), str(dependencies)))
entry = "com.guicedee.kotlin.example/com.guicedee.kotlin.example.Smoke"
results = []


def run(name, command, marker=None, timeout=120):
    print(name, flush=True)
    result = subprocess.run(command, cwd=root, capture_output=True, text=True, errors="replace", timeout=timeout)
    log = result.stdout + result.stderr
    (output / (name + ".log")).write_text(log, encoding="utf-8")
    passed = result.returncode == 0 and (marker is None or marker in log)
    results.append({"check": name, "passed": passed, "exit_code": result.returncode})
    (output / "results.json").write_text(json.dumps(results, indent=2), encoding="utf-8")
    if not passed:
        print("\n".join(log.splitlines()[-70:]))
        raise SystemExit(f"{name} failed; see {output}")
    return log


run("validate-modules", [java, "--module-path", module_path, "--validate-modules"])
run("module-path-smoke", [java, "--module-path", module_path, "-m", entry], "KOTLIN_SMOKE_OK")
if args.jlink:
    settings = subprocess.run([java, "-XshowSettings:properties", "-version"], capture_output=True, text=True, check=True)
    java_home = Path(re.search(r"java.home\s*=\s*(.+)", settings.stderr).group(1).strip())
    suffix = ".exe" if os.name == "nt" else ""
    image = output / "image"
    run("jlink", [str(java_home / "bin" / ("jlink" + suffix)),
                  "--module-path", os.pathsep.join((str(java_home / "jmods"), module_path)),
                  "--add-modules", "com.guicedee.kotlin.example", "--bind-services",
                  "--ignore-signing-information", "--strip-debug", "--no-header-files", "--no-man-pages",
                  "--launcher", "kotlin-example=com.guicedee.kotlin.example/com.guicedee.kotlin.example.Main",
                  "--output", str(image)])
    run("linked-smoke", [str(image / "bin" / ("java" + suffix)), "-m", entry], "KOTLIN_SMOKE_OK")
print(f"Runtime validation passed: {output}")
