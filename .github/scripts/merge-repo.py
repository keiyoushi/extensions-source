import gzip
import html
import sys
import json
from pathlib import Path
import re
import shutil

from google.protobuf import json_format

import index_pb2

REMOTE_REPO: Path = Path.cwd()
LOCAL_REPO: Path = REMOTE_REPO.parent.joinpath("main/repo")

to_delete: list[str] = json.loads(sys.argv[1])

for module in to_delete:
    apk_name = f"tachiyomi-{module}-v*.*.*.apk"
    icon_name = f"eu.kanade.tachiyomi.extension.{module}.png"
    for file in REMOTE_REPO.joinpath("apk").glob(apk_name):
        print(file.name)
        file.unlink(missing_ok=True)
    for file in REMOTE_REPO.joinpath("icon").glob(icon_name):
        print(file.name)
        file.unlink(missing_ok=True)

shutil.copytree(src=LOCAL_REPO.joinpath("apk"), dst=REMOTE_REPO.joinpath("apk"), dirs_exist_ok = True)
shutil.copytree(src=LOCAL_REPO.joinpath("icon"), dst=REMOTE_REPO.joinpath("icon"), dirs_exist_ok = True)

with REMOTE_REPO.joinpath("index.min.json").open() as remote_index_file:
    remote_index = json.load(remote_index_file)

with LOCAL_REPO.joinpath("index.json").open() as local_index_file:
    local_index = json.load(local_index_file)

legacy_index = [
    item for item in remote_index
    if not any([item["pkg"].endswith(f".{module}") for module in to_delete])
]
legacy_index.extend(local_index)
legacy_index.sort(key=lambda x: x["pkg"])

def extract_extension_lib(version: str) -> str:
    if match := re.search(r'(\d+)\.(\d+)', version):
        return f"{match.group(1)}.{match.group(2)}"

    raise ValueError(f"Version {version} doesn't contain MAJOR.MINOR")

index = index_pb2.Index(
    name = "Keiyoushi",
    badgeLabel = "KEI",
    signingKey = "9add655a78e96c4ec7a53ef89dccb557cb5d767489fac5e785d671a5a75d4da2",
    contact=index_pb2.Contact(
        website="https://keiyoushi.github.io",
        discord="https://discord.gg/3FbCpdKbdY"
    ),
    extensionList=index_pb2.ExtensionList(
        extensions=[
            index_pb2.Extension(
                name=extension["name"].replace("Tachiyomi: ", ""),
                packageName=extension["pkg"],
                resources=index_pb2.Resources(
                    apkUrl=f"https://raw.githubusercontent.com/keiyoushi/extensions/refs/heads/repo/apk/{extension["apk"]}",
                    iconUrl=f"https://raw.githubusercontent.com/keiyoushi/extensions/refs/heads/repo/icon/{extension["pkg"]}.png",
                ),
                extensionLib=str(extension.get("libVersion")) if "libVersion" in extension else extract_extension_lib(extension["version"]),
                versionCode=extension["code"],
                versionName=extension["version"],
                contentWarning=int(extension.get("contentWarning", 2 if extension.get("nsfw", 0) == 1 else 0)) + 1,
                sources=[
                    index_pb2.Source(
                        id=int(source["id"]),
                        name=source["name"],
                        language=source["lang"],
                        homeUrl=source["baseUrl"],
                    )
                    for source in extension["sources"]
                ]
            )
            for extension in legacy_index
        ]
    )
)

with REMOTE_REPO.joinpath("index.json").open("w", encoding="utf-8") as index_file:
    index_file.write(json_format.MessageToJson(index, always_print_fields_with_no_presence=False, preserving_proto_field_name=True))

with REMOTE_REPO.joinpath("index.pb").open("wb") as index_pb_file:
    index_pb_file.write(index.SerializeToString())

with REMOTE_REPO.joinpath("index.pb.gz").open("wb") as index_pb_file:
    index_pb_file.write(gzip.compress(index.SerializeToString()))

legacy_json_index = []
for entry in legacy_index:
    legacy_entry = entry.copy()
    cw = legacy_entry.get("contentWarning", 0)
    legacy_entry["nsfw"] = 1 if cw > 0 else 0
    legacy_entry.pop("libVersion", None)
    legacy_entry.pop("contentWarning", None)
    legacy_json_index.append(legacy_entry)

with REMOTE_REPO.joinpath("index.min.json").open("w", encoding="utf-8") as index_min_file:
    json.dump(legacy_json_index, index_min_file, ensure_ascii=False, separators=(",", ":"))

with REMOTE_REPO.joinpath("index.html").open("w", encoding="utf-8") as index_html_file:
    index_html_file.write('<!DOCTYPE html>\n<html>\n<head>\n<meta charset="UTF-8">\n<title>apks</title>\n</head>\n<body>\n<pre>\n')
    for entry in legacy_index:
        apk_escaped = 'apk/' + html.escape(entry["apk"])
        name_escaped = html.escape(entry["name"])
        index_html_file.write(f'<a href="{apk_escaped}">{name_escaped}</a>\n')
    index_html_file.write('</pre>\n</body>\n</html>\n')
