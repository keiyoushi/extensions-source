import json
import os
import re
import struct
import subprocess
from pathlib import Path
from zipfile import ZipFile

def hex_to_float(hex_str):
    int_val = int(hex_str, 16)
    float_val = struct.unpack('>f', struct.pack('>I', int_val))[0]
    return round(float_val, 1)

PACKAGE_NAME_REGEX = re.compile(r"package: name='([^']+)'")
VERSION_CODE_REGEX = re.compile(r"versionCode='([^']+)'")
VERSION_NAME_REGEX = re.compile(r"versionName='([^']+)'")
CONTENT_WARNING_REGEX = re.compile(r"'tachiyomix.contentWarning' value='([^']+)'")
EXTENSION_LIB_REGEX = re.compile(r"'tachiyomix.extensionLib' value='([^']+)'")
APPLICATION_LABEL_REGEX = re.compile(r"^application-label:'([^']+)'", re.MULTILINE)
APPLICATION_ICON_320_REGEX = re.compile(r"^application-icon-320:'([^']+)'", re.MULTILINE)
LANGUAGE_REGEX = re.compile(r"tachiyomi-([^.]+)")

*_, ANDROID_BUILD_TOOLS = (Path(os.environ["ANDROID_HOME"]) / "build-tools").iterdir()
REPO_DIR = Path("repo")
REPO_APK_DIR = REPO_DIR / "apk"
REPO_ICON_DIR = REPO_DIR / "icon"

REPO_ICON_DIR.mkdir(parents=True, exist_ok=True)

with open("output.json", encoding="utf-8") as f:
    inspector_data = json.load(f)

index_data = []

for apk in REPO_APK_DIR.iterdir():
    badging = subprocess.check_output(
        [
            ANDROID_BUILD_TOOLS / "aapt",
            "dump",
            "--include-meta-data",
            "badging",
            apk,
        ]
    ).decode()

    package_info = next(x for x in badging.splitlines() if x.startswith("package: "))
    package_name = PACKAGE_NAME_REGEX.search(package_info).group(1)
    application_icon = APPLICATION_ICON_320_REGEX.search(badging).group(1)

    with ZipFile(apk) as z, z.open(application_icon) as i, (
        REPO_ICON_DIR / f"{package_name}.png"
    ).open("wb") as f:
        f.write(i.read())

    language = LANGUAGE_REGEX.search(apk.name).group(1)
    sources = inspector_data[package_name]

    if len(sources) == 1:
        source_language = sources[0]["lang"]

        if (
            source_language != language
            and source_language not in {"all", "other"}
            and language not in {"all", "other"}
        ):
            language = source_language

    content_warning = int(CONTENT_WARNING_REGEX.search(badging).group(1))
    extension_lib = hex_to_float(EXTENSION_LIB_REGEX.search(badging).group(1).strip())

    extension_data = {
        "name": APPLICATION_LABEL_REGEX.search(badging).group(1),
        "pkg": package_name,
        "apk": apk.name,
        "lang": language,
        "code": int(VERSION_CODE_REGEX.search(package_info).group(1)),
        "version": VERSION_NAME_REGEX.search(package_info).group(1),
        "contentWarning": content_warning,
        "libVersion": extension_lib,
        "sources": [
            {
                "name": source["name"],
                "lang": source["lang"],
                "id": source["id"],
                "baseUrl": source["baseUrl"],
            }
            for source in sources
        ],
    }

    index_data.append(extension_data)

with REPO_DIR.joinpath("index.json").open("w", encoding="utf-8") as index_file:
    json.dump(index_data, index_file, ensure_ascii=False, separators=(",", ":"))
