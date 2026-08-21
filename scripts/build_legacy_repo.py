#!/usr/bin/env python3
"""Build a Tachiyomi 0.15.x-compatible static extension repository."""

from __future__ import annotations

import argparse
import ctypes
import fcntl
import hashlib
import json
import os
import re
import shutil
import sys
import tempfile
from pathlib import Path
from typing import Any


LEGACY_LIBRARIES = frozenset({"1.4"})
MODULE_PATTERN = re.compile(r"^[a-z0-9]+(?:\.[a-z0-9]+)+$")
AT_FDCWD = -100
RENAME_EXCHANGE = 0x2


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("metadata", type=Path, nargs="+")
    return parser.parse_args()


def required_string(metadata: dict[str, Any], field: str, source: Path) -> str:
    value = metadata.get(field)
    if not isinstance(value, str) or not value:
        raise ValueError(f"{source}: {field} must be a non-empty string")
    return value


def required_integer(metadata: dict[str, Any], field: str, source: Path) -> int:
    value = metadata.get(field)
    if type(value) is not int:
        raise ValueError(f"{source}: {field} must be an integer")
    return value


def load_extension(metadata_path: Path, source_root: Path) -> tuple[dict[str, Any], Path, Path]:
    try:
        metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ValueError(f"{metadata_path}: cannot read extension metadata: {error}") from error
    if not isinstance(metadata, dict):
        raise ValueError(f"{metadata_path}: metadata root must be an object")

    extension_lib = required_string(metadata, "extensionLib", metadata_path)
    if extension_lib not in LEGACY_LIBRARIES:
        accepted = ", ".join(sorted(LEGACY_LIBRARIES))
        raise ValueError(f"{metadata_path}: extensionLib {extension_lib!r} is not one of {accepted}")

    module = required_string(metadata, "module", metadata_path)
    if not MODULE_PATTERN.fullmatch(module):
        raise ValueError(f"{metadata_path}: module {module!r} is invalid")
    package_name = required_string(metadata, "packageName", metadata_path)
    if package_name != f"eu.kanade.tachiyomi.extension.{module}":
        raise ValueError(f"{metadata_path}: packageName must match module")
    name = required_string(metadata, "name", metadata_path)
    version_name = required_string(metadata, "versionName", metadata_path)
    if "/" in version_name or "\\" in version_name:
        raise ValueError(f"{metadata_path}: versionName must not contain a path separator")
    version_code = required_integer(metadata, "versionCode", metadata_path)
    content_warning = required_integer(metadata, "contentWarning", metadata_path)
    sources = metadata.get("sources")
    if not isinstance(sources, list) or not sources:
        raise ValueError(f"{metadata_path}: sources must be a non-empty array")

    legacy_sources = []
    for position, source in enumerate(sources):
        if not isinstance(source, dict):
            raise ValueError(f"{metadata_path}: sources[{position}] must be an object")
        source_id = source.get("id")
        if type(source_id) is not int:
            raise ValueError(f"{metadata_path}: sources[{position}].id must be an integer")
        legacy_sources.append(
            {
                "id": source_id,
                "name": required_string(source, "name", metadata_path),
                "lang": required_string(source, "lang", metadata_path),
                "baseUrl": required_string(source, "baseUrl", metadata_path),
            },
        )

    apk_directory = metadata_path.parent / "outputs" / "apk" / "release"
    apk_name = f"tachiyomi-{module}-v{version_name}.apk"
    apk = apk_directory / apk_name
    if not apk.is_file():
        raise ValueError(f"{metadata_path}: expected release APK {apk_name}")

    module_path = Path(*module.split("."))
    icon = source_root / "src" / module_path / "res" / "mipmap-xhdpi" / "ic_launcher.png"
    theme = metadata.get("theme")
    if not icon.is_file() and theme is not None:
        if not isinstance(theme, str) or not theme or "/" in theme or "\\" in theme:
            raise ValueError(f"{metadata_path}: theme must be a simple non-empty string or null")
        icon = source_root / "lib-multisrc" / theme / "res" / "mipmap-xhdpi" / "ic_launcher.png"
    if not icon.is_file():
        icon = source_root / "core" / "src" / "main" / "res" / "mipmap-xhdpi" / "ic_launcher.png"

    legacy_entry = {
        "name": f"Tachiyomi: {name}",
        "pkg": package_name,
        "apk": apk.name,
        "lang": module.split(".", maxsplit=1)[0],
        "code": version_code,
        "version": version_name,
        "nsfw": int(content_warning > 1),
        "sources": legacy_sources,
    }
    return legacy_entry, apk, icon


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for chunk in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()

def replace_output_directory(staging: Path, output: Path, backup: Path) -> None:
    lock_path = output.with_name(f".{output.name}.lock")
    with lock_path.open("w") as lock_file:
        fcntl.flock(lock_file, fcntl.LOCK_EX)
        if not output.exists():
            os.replace(staging, output)
            return

        if backup.exists():
            shutil.rmtree(backup)

        libc = ctypes.CDLL(None, use_errno=True)
        renameat2 = getattr(libc, "renameat2", None)
        if renameat2 is None:
            raise OSError("atomic directory replacement requires libc renameat2")
        renameat2.argtypes = (ctypes.c_int, ctypes.c_char_p, ctypes.c_int, ctypes.c_char_p, ctypes.c_uint)
        renameat2.restype = ctypes.c_int
        if renameat2(AT_FDCWD, os.fsencode(staging), AT_FDCWD, os.fsencode(output), RENAME_EXCHANGE) != 0:
            error = ctypes.get_errno()
            raise OSError(error, os.strerror(error), output)

        try:
            os.replace(staging, backup)
        except OSError:
            # The exchange already published output. Keep the prior output in staging.
            pass


def build_repository(output: Path, source_root: Path, metadata_paths: list[Path]) -> int:
    extensions = [load_extension(path, source_root) for path in metadata_paths]
    extensions.sort(key=lambda extension: extension[0]["pkg"])
    packages = [extension[0]["pkg"] for extension in extensions]
    if len(packages) != len(set(packages)):
        raise ValueError("duplicate package names in repository input")

    output = output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    staging = Path(tempfile.mkdtemp(prefix=f".{output.name}.", dir=output.parent))
    backup = output.with_name(f".{output.name}.previous")
    try:
        apk_directory = staging / "apk"
        icon_directory = staging / "icon"
        apk_directory.mkdir()
        icon_directory.mkdir()
        asset_manifest = []
        for entry, apk, icon in extensions:
            destination_apk = apk_directory / entry["apk"]
            destination_icon = icon_directory / f"{entry['pkg']}.png"
            shutil.copyfile(apk, destination_apk)
            shutil.copyfile(icon, destination_icon)
            asset_manifest.append(
                {
                    "apk": f"apk/{destination_apk.name}",
                    "apkSha256": sha256(destination_apk),
                    "icon": f"icon/{destination_icon.name}",
                    "iconSha256": sha256(destination_icon),
                    "pkg": entry["pkg"],
                },
            )

        (staging / "index.min.json").write_text(
            json.dumps([entry for entry, _, _ in extensions], separators=(",", ":")),
            encoding="utf-8",
        )
        (staging / "manifest.json").write_text(
            json.dumps({"assets": asset_manifest}, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        replace_output_directory(staging, output, backup)
    except Exception:
        if staging.exists():
            shutil.rmtree(staging, ignore_errors=True)
        raise

    print(f"Generated {len(extensions)} legacy extension entries in {output}")
    return 0


def main() -> int:
    arguments = parse_arguments()
    try:
        return build_repository(arguments.output, arguments.source_root.resolve(), arguments.metadata)
    except (OSError, ValueError) as error:
        print(error, file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
