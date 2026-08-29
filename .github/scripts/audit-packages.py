import re
import sys
from pathlib import Path

EXTENSION_PREFIX = "eu.kanade.tachiyomi.extension"
PKG_NAME_REGEX = re.compile(r"""pkgName\s*=\s*["']([^"']+)["']""")
SUFFIX_REGEX = re.compile(r"^[a-z0-9_]+(\.[a-z0-9_]+)+$")


def audit() -> list[str]:
    errors: list[str] = []
    owners: dict[str, str] = {}

    build_files = sorted(Path("src").glob("*/*/build.gradle.kts"))

    for build_file in build_files:
        lang, name = build_file.parts[1], build_file.parts[2]
        default = f"{lang}.{name}"
        suffix = default

        match = PKG_NAME_REGEX.search(build_file.read_text("utf-8"))
        if match:
            suffix = match.group(1)
            if suffix.startswith(f"{EXTENSION_PREFIX}."):
                errors.append(
                    f"{build_file}: pkgName must be the suffix only "
                    f"(e.g. '{default}'), not a full package name"
                )
                continue
            if not SUFFIX_REGEX.match(suffix):
                errors.append(
                    f"{build_file}: invalid pkgName '{suffix}' - expected "
                    f"lowercase dot-separated segments (e.g. '{default}')"
                )
                continue

        if suffix in owners:
            errors.append(
                f"package conflict: {EXTENSION_PREFIX}.{suffix} is produced by both "
                f"{owners[suffix]} and {build_file.parent.as_posix()}"
            )
        else:
            owners[suffix] = build_file.parent.as_posix()

    return errors


def main() -> None:
    errors = audit()

    if errors:
        for error in errors:
            print(error)
        sys.exit(1)

    modules = len(list(Path("src").glob("*/*/build.gradle.kts")))
    print(f"No package conflicts across {modules} extensions.")


if __name__ == "__main__":
    main()
