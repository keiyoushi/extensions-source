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

LANGUAGE_REGEX = re.compile(r"tachiyomi-([^.]+)")

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

shutil.copytree(
    src=LOCAL_REPO.joinpath("apk"), dst=REMOTE_REPO.joinpath("apk"), dirs_exist_ok=True
)
shutil.copytree(
    src=LOCAL_REPO.joinpath("icon"),
    dst=REMOTE_REPO.joinpath("icon"),
    dirs_exist_ok=True,
)

with REMOTE_REPO.joinpath("index.json").open() as f:
    remote_proto = json_format.Parse(f.read(), index_pb2.Index())

with LOCAL_REPO.joinpath("index.json").open() as f:
    local_proto = json_format.Parse(f.read(), index_pb2.ExtensionList())

all_extensions = [
    ext
    for ext in remote_proto.extensionList.extensions
    if not any(ext.packageName.endswith(f".{module}") for module in to_delete)
]
all_extensions.extend(local_proto.extensions)
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

with REMOTE_REPO.joinpath("index.json").open("w", encoding="utf-8") as f:
    f.write(
        json_format.MessageToJson(
            index,
            always_print_fields_with_no_presence=False,
            preserving_proto_field_name=True,
        )
    )

with REMOTE_REPO.joinpath("index.pb").open("wb") as f:
    f.write(index.SerializeToString())

with REMOTE_REPO.joinpath("index.pb.gz").open("wb") as f:
    f.write(gzip.compress(index.SerializeToString()))


def get_legacy_lang(ext) -> str:
    apk_filename = ext.resources.apkUrl.split("/")[-1]
    lang = LANGUAGE_REGEX.search(apk_filename).group(1)
    if len(ext.sources) == 1:
        source_language = ext.sources[0].language
        if (
            source_language != lang
            and source_language not in {"all", "other"}
            and lang not in {"all", "other"}
        ):
            lang = source_language
    return lang


legacy_json_index = [
    {
        "name": f"Tachiyomi: {ext.name}",
        "pkg": ext.packageName,
        "apk": ext.resources.apkUrl.split("/")[-1],
        "lang": get_legacy_lang(ext),
        "code": ext.versionCode,
        "version": ext.versionName,
        "nsfw": 1 if ext.contentWarning > 2 else 0,
        "sources": [
            {
                "name": source.name,
                "lang": source.language,
                "id": str(source.id),
                "baseUrl": source.homeUrl,
            }
            for source in ext.sources
        ],
    }
    for ext in all_extensions
]

with REMOTE_REPO.joinpath("index.min.json").open("w", encoding="utf-8") as f:
    json.dump(legacy_json_index, f, ensure_ascii=False, separators=(",", ":"))

with REMOTE_REPO.joinpath("index.html").open("w", encoding="utf-8") as f:
    f.write(
        '<!DOCTYPE html>\n<html>\n<head>\n<meta charset="UTF-8">\n<title>apks</title>\n</head>\n<body>\n<pre>\n'
    )
    for ext in all_extensions:
        apk_escaped = html.escape(ext.resources.apkUrl)
        name_escaped = html.escape(f"Tachiyomi: {ext.name}")
        f.write(f'<a href="{apk_escaped}">{name_escaped}</a>\n')
    f.write("</pre>\n</body>\n</html>\n")
