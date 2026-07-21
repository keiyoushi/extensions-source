import json
import os
import re
import struct
import subprocess
from pathlib import Path
from zipfile import ZipFile


def hex_to_float(hex_str):
    int_val = int(hex_str, 16)
    float_val = struct.unpack(">f", struct.pack(">I", int_val))[0]
    return round(float_val, 1)


PACKAGE_NAME_REGEX = re.compile(r"package: name='([^']+)'")
VERSION_CODE_REGEX = re.compile(r"versionCode='([^']+)'")
VERSION_NAME_REGEX = re.compile(r"versionName='([^']+)'")
CONTENT_WARNING_REGEX = re.compile(r"'tachiyomix.contentWarning' value='([^']+)'")
EXTENSION_LIB_REGEX = re.compile(r"'tachiyomix.extensionLib' value='([^']+)'")
EXTENSION_NAME_REGEX = re.compile(r"'tachiyomix.name' value='([^']+)'")
APPLICATION_ICON_320_REGEX = re.compile(
    r"^application-icon-320:'([^']+)'", re.MULTILINE
)

*_, ANDROID_BUILD_TOOLS = (Path(os.environ["ANDROID_HOME"]) / "build-tools").iterdir()
REPO_DIR = Path("repo")
REPO_APK_DIR = REPO_DIR / "apk"
REPO_ICON_DIR = REPO_DIR / "icon"

REPO_ICON_DIR.mkdir(parents=True, exist_ok=True)

APK_BASE_URL = (
    "https://raw.githubusercontent.com/keiyoushi/extensions/refs/heads/repo/apk"
)
ICON_BASE_URL = (
    "https://raw.githubusercontent.com/keiyoushi/extensions/refs/heads/repo/icon"
)

with open("output.json", encoding="utf-8") as f:
    inspector_data = json.load(f)

extensions = []

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

    with (
        ZipFile(apk) as z,
        z.open(application_icon) as i,
        (REPO_ICON_DIR / f"{package_name}.png").open("wb") as f,
    ):
        f.write(i.read())

    sources = inspector_data[package_name]
    content_warning = int(CONTENT_WARNING_REGEX.search(badging).group(1))
    extension_lib = hex_to_float(EXTENSION_LIB_REGEX.search(badging).group(1).strip())
    ext_name = EXTENSION_NAME_REGEX.search(badging).group(1)

    extensions.append(
        {
            "name": ext_name,
            "packageName": package_name,
            "resources": {
                "apkUrl": f"{APK_BASE_URL}/{apk.name}",
                "iconUrl": f"{ICON_BASE_URL}/{package_name}.png",
            },
            "extensionLib": str(extension_lib),
            "versionCode": int(VERSION_CODE_REGEX.search(package_info).group(1)),
            "versionName": VERSION_NAME_REGEX.search(package_info).group(1),
            "contentWarning": content_warning + 1,
            "sources": [
                {
                    "id": int(source["id"]),
                    "name": source["name"],
                    "language": source["lang"],
                    "homeUrl": source["baseUrl"],
                }
                for source in sources
            ],
        }
    )

with REPO_DIR.joinpath("index.json").open("w", encoding="utf-8") as f:
    json.dump({"extensions": extensions}, f, ensure_ascii=False, separators=(",", ":"))
