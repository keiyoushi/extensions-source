#!/usr/bin/env python3
"""List Keiyoushi extension Gradle tasks compatible with Tachiyomi 0.15.x."""

from __future__ import annotations

import argparse
import re
from pathlib import Path


LEGACY_LIBRARY_ASSIGNMENT = re.compile(r'^\s*libVersion\s*=\s*"1\.[45]"\s*$', re.MULTILINE)


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", type=Path, required=True)
    return parser.parse_args()


def list_tasks(source_root: Path) -> list[str]:
    modules = []
    for build_file in source_root.glob("src/*/*/build.gradle.kts"):
        if LEGACY_LIBRARY_ASSIGNMENT.search(build_file.read_text(encoding="utf-8")) is None:
            continue
        module = build_file.parent.relative_to(source_root / "src")
        if len(module.parts) != 2:
            continue
        modules.append(f":src:{module.parts[0]}:{module.parts[1]}:assembleRelease")
    return sorted(modules)


def main() -> int:
    arguments = parse_arguments()
    for task in list_tasks(arguments.source_root.resolve()):
        print(task)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
