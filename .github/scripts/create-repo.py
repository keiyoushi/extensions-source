import json
import os
import re
import subprocess
from pathlib import Path
from zipfile import ZipFile

PACKAGE_NAME_REGEX = re.compile(r"package: name='([^']+)'")
VERSION_CODE_REGEX = re.compile(r"versionCode='([^']+)'")
VERSION_NAME_REGEX = re.compile(r"versionName='([^']+)'")
IS_NSFW_REGEX = re.compile(r"'tachiyomi.extension.nsfw' value='([^']+)'")
APPLICATION_LABEL_REGEX = re.compile(r"^application-label:'([^']+)'", re.MULTILINE)
APPLICATION_ICON_320_REGEX = re.compile(r"^application-icon-320:'([^']+)'", re.MULTILINE)
LANGUAGE_REGEX = re.compile(r"tachiyomi-([^.]+)")

REPO_DIR = Path("repo")
REPO_APK_DIR = REPO_DIR / "apk"
REPO_ICON_DIR = REPO_DIR / "icon"

REPO_ICON_DIR.mkdir(parents=True, exist_ok=True)

index_min_data = []

for apk in sorted(REPO_APK_DIR.iterdir()):
    if not apk.name.endswith(".apk"):
        continue

    try:
        badging = subprocess.check_output(
            ["aapt2", "dump", "badging", str(apk)]
        ).decode()
    except Exception as e:
        print(f"Skipping {apk.name}: {e}")
        continue

    package_info = next(
        (x for x in badging.splitlines() if x.startswith("package: ")), None
    )
    if not package_info:
        continue

    package_name = PACKAGE_NAME_REGEX.search(package_info).group(1)

    icon_match = APPLICATION_ICON_320_REGEX.search(badging)
    if icon_match:
        application_icon = icon_match.group(1)
        try:
            with ZipFile(apk) as z, z.open(application_icon) as i, (
                REPO_ICON_DIR / f"{package_name}.png"
            ).open("wb") as f:
                f.write(i.read())
        except Exception:
            pass

    language = LANGUAGE_REGEX.search(apk.name).group(1)

    label_match = APPLICATION_LABEL_REGEX.search(badging)
    label = label_match.group(1) if label_match else apk.name

    nsfw_match = IS_NSFW_REGEX.search(badging)
    nsfw = int(nsfw_match.group(1)) if nsfw_match else 0

    min_data = {
        "name": label,
        "pkg": package_name,
        "apk": apk.name,
        "lang": language,
        "code": int(VERSION_CODE_REGEX.search(package_info).group(1)),
        "version": VERSION_NAME_REGEX.search(package_info).group(1),
        "nsfw": nsfw,
        "sources": [],
    }

    index_min_data.append(min_data)

with REPO_DIR.joinpath("index.min.json").open("w", encoding="utf-8") as f:
    json.dump(index_min_data, f, ensure_ascii=False, separators=(",", ":"))

print(f"Generated index with {len(index_min_data)} extensions")
