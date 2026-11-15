#!/usr/bin/env python3
"""Verify the JPMS wrapper preserves its supported upstream coroutine classes and metadata."""
import argparse
from pathlib import Path
from zipfile import ZipFile

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--upstream-version", default="3.3.1", help="Must match the platform's smallrye.mutiny.version")
parser.add_argument("--repository", type=Path, default=Path.home() / ".m2/repository")
args = parser.parse_args()
upstream = args.repository / f"io/smallrye/reactive/mutiny-kotlin/{args.upstream_version}/mutiny-kotlin-{args.upstream_version}.jar"
wrapped = root / "mutiny-kotlin/target/mutiny-kotlin-2.6.0.jar"
with ZipFile(upstream) as source, ZipFile(wrapped) as target:
    expected = {name for name in source.namelist() if
                (name.startswith("io/smallrye/mutiny/coroutines/") and name.endswith(".class")
                 and not name.startswith("io/smallrye/mutiny/coroutines/MultiBuilders"))
                or name.endswith(".kotlin_module")}
    assert expected, "Missing upstream coroutine entries"
    for name in expected:
        assert source.read(name) == target.read(name), f"Upstream entry changed: {name}"
    classes = {name for name in target.namelist() if name.endswith(".class") and name != "module-info.class"}
    assert classes == {name for name in expected if name.endswith(".class")}, "Unexpected/missing wrapper classes"
    assert "module-info.class" in target.namelist(), "Missing explicit module descriptor"
    print(f"PASS: {len(expected)} supported upstream class/metadata entries unchanged; no split-package classes")
