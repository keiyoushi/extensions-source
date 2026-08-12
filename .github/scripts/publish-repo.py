import gzip
import html
import json
import math
import subprocess
import sys
from pathlib import Path

from google.protobuf import json_format

import index_pb2

# Artifacts downloaded from the build jobs: one APK per extension plus the source metadata JSON
# emitted by each assembleRelease.
ARTIFACTS_DIR = Path.home() / "apk-artifacts"

# The checked-out `repo` branch we publish into (the working directory).
REPO_DIR = Path.cwd()
REPO_APK_DIR = REPO_DIR / "apk"
REPO_JAR_DIR = REPO_DIR / "jar"
REPO_APK_DIR.mkdir(parents=True, exist_ok=True)
REPO_JAR_DIR.mkdir(parents=True, exist_ok=True)

APK_BASE_URL = "https://cdn.jsdelivr.net/gh/keiyoushi/extensions@repo/apk"
JAR_BASE_URL = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/jar"
ICON_BASE_URL = "https://cdn.jsdelivr.net/gh/keiyoushi/extensions-source@main"

to_delete: list[str] = json.loads(sys.argv[1])
current_sha = sys.argv[2]
current_sha_short = current_sha[:7]

# Drop apks/icons for modules that were deleted or rebuilt (rebuilt ones are re-added below).
for module in to_delete:
    for file in REPO_APK_DIR.glob(f"tachiyomi-{module}-v*.*.*.apk"):
        print(f"removing {file.name}")
        file.unlink(missing_ok=True)
    for file in REPO_JAR_DIR.glob(f"tachiyomi-{module}-v*.*.*.jar"):
        print(f"removing {file.name}")
        file.unlink(missing_ok=True)

# Build index entries for the freshly built apks. Each extension's metadata comes from the
# source-info JSON emitted by its assembleRelease task (see GenerateSourceInfoTask); its APK is a
# sibling in the same build dir. aapt reads the icon out of the APK
new_extensions: list[tuple[index_pb2.Extension, Path, Path]] = []

SOURCE_DIR = Path(__file__).resolve().parents[2]
ICON_FILE = "res/mipmap-xhdpi/ic_launcher.png"


def get_icon_url(module: str, theme: str | None) -> str:
    module_icon = f"src/{module.replace('.', '/')}/{ICON_FILE}"
    if (SOURCE_DIR / module_icon).exists():
        return f"{ICON_BASE_URL}/{module_icon}"

    if theme:
        theme_icon = f"lib-multisrc/{theme}/{ICON_FILE}"
        if (SOURCE_DIR / theme_icon).exists():
            return f"{ICON_BASE_URL}/{theme_icon}"

    return f"{ICON_BASE_URL}/core/src/main/{ICON_FILE}"

for info_file in ARTIFACTS_DIR.glob("**/keiyoushi-source-info.json"):
    with info_file.open(encoding="utf-8") as f:
        info = json.load(f)
    package_name = info["packageName"]
    apk = next((info_file.parent / "outputs/apk/release").glob("*.apk"), None)
    if apk is None:
        raise FileNotFoundError(
            f"{package_name}: no release apk found under {info_file.parent}"
        )

    jar = next((info_file.parent / "outputs/jar/release").glob("*.jar"), None)
    if jar is None:
        raise FileNotFoundError(
            f"{package_name}: no release jar found under {info_file.parent}"
        )

    (REPO_APK_DIR / apk.name).write_bytes(apk.read_bytes())
    (REPO_JAR_DIR / jar.name).write_bytes(jar.read_bytes())

    ext = index_pb2.Extension(
        name=info["name"],
        packageName=package_name,
        resources=index_pb2.Resources(
            apkUrl=f"{APK_BASE_URL}/{apk.name}",
            jarUrl=f"{JAR_BASE_URL}/{jar.name}",
            iconUrl=get_icon_url(info["module"], info.get("theme")),
        ),
        extensionLib=info["extensionLib"],
        versionCode=info["versionCode"],
        versionName=info["versionName"],
        contentWarning=info["contentWarning"],
        sources=[
            index_pb2.Source(
                id=int(source["id"]),
                name=source["name"],
                language=source["lang"],
                homeUrl=source["baseUrl"],
                mirrorUrls=source.get("mirrorUrls", []),
            )
            for source in info["sources"]
        ],
    )
    new_extensions.append((ext, apk, jar))

# Merge with the already-published index, dropping the deleted/rebuilt modules.
with REPO_DIR.joinpath("index.json").open() as f:
    remote_proto = json_format.Parse(f.read(), index_pb2.Index())

all_extensions = [
    ext
    for ext in remote_proto.extensionList.extensions
    if not any(ext.packageName.endswith(f".{module}") for module in to_delete)
]
all_extensions.extend([i[0] for i in new_extensions])
all_extensions.sort(key=lambda ext: ext.packageName)

index = index_pb2.Index(
    name="Keiyoushi",
    badgeLabel="KEI",
    signingKey="9add655a78e96c4ec7a53ef89dccb557cb5d767489fac5e785d671a5a75d4da2",
    contact=index_pb2.Contact(
        website="https://keiyoushi.github.io", discord="https://discord.gg/3FbCpdKbdY"
    ),
    extensionList=index_pb2.ExtensionList(extensions=all_extensions),
)

with REPO_DIR.joinpath("index.json").open("w", encoding="utf-8") as f:
    f.write(
        json_format.MessageToJson(
            index,
            always_print_fields_with_no_presence=False,
            preserving_proto_field_name=True,
        )
    )

with REPO_DIR.joinpath("index.pb").open("wb") as f:
    f.write(gzip.compress(index.SerializeToString(deterministic=True)))

with REPO_DIR.joinpath("index.html").open("w", encoding="utf-8") as f:
    f.write(
        '<!DOCTYPE html>\n<html>\n<head>\n<meta charset="UTF-8">\n<title>apks</title>\n</head>\n<body>\n<pre>\n'
    )
    for ext in all_extensions:
        apk_escaped = html.escape(ext.resources.apkUrl)
        name_escaped = html.escape(f"Tachiyomi: {ext.name}")
        f.write(f'<a href="{apk_escaped}">{name_escaped}</a>\n')
    f.write("</pre>\n</body>\n</html>\n")

# --- Upload assets as release ---
if not new_extensions:
    sys.exit(0)

REPO_NAME = "keiyoushi/extensions"
ASSET_LIMIT = 495 # Actual limit is 1000 but we upload 2 item per extension.
total_extensions = len(new_extensions)
release_count = (total_extensions // ASSET_LIMIT) + 1
ext_per_release = math.ceil(total_extensions / release_count)

def run_gh(*args: str, success_codes: list[int] = []) -> str:
    result = subprocess.run(["gh", *args], capture_output=True, text=True)
    if result.returncode == 0 or result.returncode in success_codes:
        return result.stdout.strip()

    print(f"gh {' '.join(args)} failed: {result.stderr}")
    sys.exit(result.returncode)

def create_release(tag: str):
    print(f"Creating release {tag}")
    run_gh(
        "release", "create", tag,
        "--repo", REPO_NAME,
        "--title", f"Repository Update {tag}",
        "--notes", f"Automated update from keiyoushi/extensions-source@{current_sha}",
    )

def upload_assets(tag: str, files: list[Path]):
    if not files:
        return
    print(f"Uploading {len(files)} assets to {tag}")
    run_gh("release", "upload", tag, *[str(f) for f in files], "--repo", REPO_NAME, "--clobber")

def get_release_tag(c_index: int) -> str:
    return f"{current_sha_short}-{c_index}" if release_count > 1 else current_sha_short

for i in range(0, total_extensions, ext_per_release):
    batch = new_extensions[i:i + ext_per_release]
    tag = get_release_tag(i // ext_per_release)
    create_release(tag)

    files_to_upload = []
    for ext, apk, jar in batch:
        files_to_upload.extend([apk, jar])

    upload_assets(tag, files_to_upload)
