import os
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

EXTENSION_PREFIX = "eu.kanade.tachiyomi.extension"
PKG_NAME_REGEX = re.compile(r"""pkgName\s*=\s*["']([^"']+)["']""")
SUFFIX_REGEX = re.compile(r"""^\w+(\.\w+)+$""", re.ASCII)


@dataclass
class Diagnostic:
    message: str
    file: str | None = None
    line: int | None = None


@dataclass
class AuditResult:
    errors: list[Diagnostic] = field(default_factory=list)
    warnings: list[Diagnostic] = field(default_factory=list)
    modules: int = 0


def audit() -> AuditResult:
    result = AuditResult()
    owners: dict[str, str] = {}

    build_files = sorted(Path("src").glob("*/*/build.gradle.kts"))
    result.modules = len(build_files)

    for build_file in build_files:
        module = build_file.parent.as_posix()
        lang, name = build_file.parts[1], build_file.parts[2]
        default = f"{lang}.{name}"
        suffix = default
        suffix_line = 1

        content = build_file.read_text("utf-8")
        match = PKG_NAME_REGEX.search(content)
        if match:
            suffix = match.group(1)
            suffix_line = content.count("\n", 0, match.start()) + 1
            if suffix.startswith(f"{EXTENSION_PREFIX}."):
                result.errors.append(Diagnostic(
                    f"pkgName must be the suffix only (e.g. '{default}'), "
                    f"not a full package name",
                    file=module,
                    line=suffix_line,
                ))
                continue
            if not SUFFIX_REGEX.match(suffix):
                result.errors.append(Diagnostic(
                    f"invalid pkgName '{suffix}' - expected dot-separated "
                    f"alphanumeric segments (e.g. '{default}')",
                    file=module,
                    line=suffix_line,
                ))
                continue

        if suffix != suffix.lower():
            result.warnings.append(Diagnostic(
                f"suffix '{suffix}' contains uppercase characters - prefer "
                f"renaming the module directory (keeping the package with "
                f"pkgName) or a lowercase pkgName",
                file=module,
                line=suffix_line,
            ))

        if suffix in owners:
            result.errors.append(Diagnostic(
                f"package conflict: {EXTENSION_PREFIX}.{suffix} is also "
                f"produced by {owners[suffix]}",
                file=module,
                line=suffix_line,
            ))
        else:
            owners[suffix] = module

    return result


def print_diagnostic(diag: Diagnostic, level: str) -> None:
    if os.getenv("GITHUB_ACTIONS") == "true":
        fields = f"file={diag.file},line={diag.line or 1}" if diag.file else ""
        location = f" {fields}" if fields else ""
        print(f"::{level}{location}::{diag.message}")
    else:
        location = f"{diag.file}:{diag.line}: " if diag.file else ""
        print(f"{location}{diag.message}")


def main() -> None:
    result = audit()

    for warning in result.warnings:
        print_diagnostic(warning, "warning")

    if result.errors:
        for error in result.errors:
            print_diagnostic(error, "error")
        sys.exit(1)

    print(f"No package conflicts across {result.modules} extensions.")


if __name__ == "__main__":
    main()
