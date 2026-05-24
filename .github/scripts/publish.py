import json
import os
import re
import subprocess
import sys
from pathlib import Path
from zipfile import ZipFile

PACKAGE_NAME_REGEX = re.compile(r"package: name='([^']+)'")
VERSION_CODE_REGEX = re.compile(r"versionCode='([^']+)'")
VERSION_NAME_REGEX = re.compile(r"versionName='([^']+)'")
IS_NSFW_REGEX = re.compile(r"'tachiyomi.extension.nsfw' value='([^']+)'")
APPLICATION_LABEL_REGEX = re.compile(r"^application-label:'([^']+)'", re.MULTILINE)
APPLICATION_ICON_320_REGEX = re.compile(r"^application-icon-320:'([^']+)'", re.MULTILINE)
LANGUAGE_REGEX = re.compile(r"tachiyomi-([^.]+)")

apk_dir = Path(sys.argv[1])
repo_dir = Path("repo")
repo_apk_dir = repo_dir / "apk"
repo_icon_dir = repo_dir / "icon"

repo_apk_dir.mkdir(parents=True, exist_ok=True)
repo_icon_dir.mkdir(parents=True, exist_ok=True)

index_min_data = []

for apk in sorted(apk_dir.rglob("*.apk")):
    badging = subprocess.check_output(
        ["aapt", "dump", "--include-meta-data", "badging", str(apk)]
    ).decode()

    package_info = next(x for x in badging.splitlines() if x.startswith("package: "))
    package_name = PACKAGE_NAME_REGEX.search(package_info).group(1)
    version_code = int(VERSION_CODE_REGEX.search(package_info).group(1))
    version_name = VERSION_NAME_REGEX.search(package_info).group(1)
    is_nsfw = int(IS_NSFW_REGEX.search(badging).group(1))
    app_label = APPLICATION_LABEL_REGEX.search(badging).group(1)
    app_icon = APPLICATION_ICON_320_REGEX.search(badging).group(1)
    language = LANGUAGE_REGEX.search(apk.name).group(1)

    apk_name = apk.name

    import shutil
    shutil.copy2(apk, repo_apk_dir / apk_name)

    with ZipFile(apk) as z, z.open(app_icon) as i, (
        repo_icon_dir / f"{package_name}.png"
    ).open("wb") as f:
        f.write(i.read())

    min_data = {
        "name": app_label,
        "pkg": package_name,
        "apk": apk_name,
        "lang": language,
        "code": version_code,
        "version": version_name,
        "nsfw": is_nsfw,
        "sources": [],
    }

    index_min_data.append(min_data)

(repo_dir / "index.min.json").write_text(
    json.dumps(index_min_data, ensure_ascii=False, separators=(",", ":"))
)
