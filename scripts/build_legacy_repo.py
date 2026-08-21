#!/usr/bin/env python3
"""Build a Tachiyomi 0.15.x-compatible static extension repository."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import sys
import tempfile
from pathlib import Path
from typing import Any


LEGACY_LIBRARIES = frozenset({"1.4", "1.5"})
MODULE_PATTERN = re.compile(r"^[a-z0-9]+(?:\.[a-z0-9]+)+$")


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
    if not isinstance(value, int):
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
    name = required_string(metadata, "name", metadata_path)
    version_name = required_string(metadata, "versionName", metadata_path)
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
        if not isinstance(source_id, int):
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
    apks = sorted(apk_directory.glob("*.apk"))
    if len(apks) != 1:
        raise ValueError(f"{metadata_path}: expected one release APK in {apk_directory}, found {len(apks)}")

    module_path = Path(*module.split("."))
    icon = source_root / "src" / module_path / "res" / "mipmap-xhdpi" / "ic_launcher.png"
    if not icon.is_file():
        icon = source_root / "core" / "src" / "main" / "res" / "mipmap-xhdpi" / "ic_launcher.png"
    if not icon.is_file():
        raise ValueError(f"{metadata_path}: no icon found for {module}")

    legacy_entry = {
        "name": f"Tachiyomi: {name}",
        "pkg": package_name,
        "apk": apks[0].name,
        "lang": module.split(".", maxsplit=1)[0],
        "code": version_code,
        "version": version_name,
        "nsfw": int(content_warning > 1),
        "sources": legacy_sources,
    }
    return legacy_entry, apks[0], icon


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for chunk in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


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

        if backup.exists():
            shutil.rmtree(backup)
        if output.exists():
            os.replace(output, backup)
        os.replace(staging, output)
        if backup.exists():
            shutil.rmtree(backup)
    except Exception:
        shutil.rmtree(staging, ignore_errors=True)
        if backup.exists() and not output.exists():
            os.replace(backup, output)
        raise

    print(f"Generated {len(extensions)} legacy extension entries in {output}")
    return 0


def main() -> int:
    arguments = parse_arguments()
    try:
        return build_repository(arguments.output, arguments.source_root.resolve(), arguments.metadata)
    except ValueError as error:
        print(error, file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
