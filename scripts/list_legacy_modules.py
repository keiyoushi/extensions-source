#!/usr/bin/env python3
"""List Keiyoushi extension Gradle tasks compatible with Tachiyomi 0.15.x."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


LEGACY_LIBRARY_ASSIGNMENT = re.compile(r'^\s*libVersion\s*=\s*"1\.4"\s*$', re.MULTILINE)
MODULE_PATTERN = re.compile(r"^[a-z][a-z0-9]*/[a-z][a-z0-9]*$")


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", type=Path, required=True)
    return parser.parse_args()


def list_tasks(source_root: Path) -> list[str]:
    modules_path = source_root / "legacy-modules.txt"
    if not modules_path.is_file():
        raise ValueError(f"missing verified legacy module list: {modules_path}")

    modules = []
    for raw_module in modules_path.read_text(encoding="utf-8").splitlines():
        module = raw_module.partition("#")[0].strip()
        if not module:
            continue
        if MODULE_PATTERN.fullmatch(module) is None:
            raise ValueError(f"invalid legacy module: {module}")

        language, name = module.split("/")
        build_file = source_root / "src" / language / name / "build.gradle.kts"
        if not build_file.is_file():
            raise ValueError(f"missing legacy module build file: {build_file}")
        if LEGACY_LIBRARY_ASSIGNMENT.search(build_file.read_text(encoding="utf-8")) is None:
            raise ValueError(f"legacy module does not use extension library 1.4: {module}")
        modules.append(f":src:{language}:{name}:assembleRelease")

    if len(modules) != len(set(modules)):
        raise ValueError("duplicate module in verified legacy module list")
    return sorted(modules)


def main() -> int:
    arguments = parse_arguments()
    try:
        tasks = list_tasks(arguments.source_root.resolve())
    except ValueError as error:
        print(error, file=sys.stderr)
        return 1
    for task in tasks:
        print(task)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
