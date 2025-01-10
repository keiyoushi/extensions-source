import html
import sys
import json
from pathlib import Path
import shutil

REMOTE_REPO: Path = Path.cwd()
LOCAL_REPO: Path = REMOTE_REPO.parent.joinpath("main/repo")

to_delete: list[str] = json.loads(sys.argv[1])

for module in to_delete:
    apk_name = f"tachiyomi-{module}-v*.*.*.apk"
    icon_name = f"eu.kanade.tachiyomi.extension.{module}.png"
    for file in REMOTE_REPO.joinpath("apk").glob(apk_name):
        file.unlink()
    for file in REMOTE_REPO.joinpath("icon").glob(icon_name):
        file.unlink()

shutil.copytree(src=LOCAL_REPO.joinpath("apk"), dst=REMOTE_REPO.joinpath("apk"))
shutil.copytree(src=LOCAL_REPO.joinpath("icon"), dst=REMOTE_REPO.joinpath("icon"))

with REMOTE_REPO.joinpath("index.min.json").open() as remote_index_file:
    remote_index = json.load(remote_index_file)

with LOCAL_REPO.joinpath("index.min.json").open() as local_index_file:
    local_index = json.load(local_index_file)

index = [
    item for item in remote_index
    if not any([item["pkg"].endswith(f".{module}") for module in to_delete])
]
index.extend(local_index)
index.sort(key=lambda x: x["pkg"])

with REMOTE_REPO.joinpath("index.json").open("w", encoding="utf-8") as index_file:
    json.dump(index, index_file, ensure_ascii=False, indent=2)

with REMOTE_REPO.joinpath("index.min.json").open("w", encoding="utf-8") as index_min_file:
    json.dump(index, index_min_file, ensure_ascii=False, separators=(",", ":"))

with REMOTE_REPO.joinpath("index.html").open("w", encoding="utf-8") as index_html_file:
    index_html_file.write('<!DOCTYPE html>\n<html>\n<head>\n<meta charset="UTF-8">\n<title>apks</title>\n</head>\n<body>\n<pre>\n')
    for entry in index:
        apk_escaped = 'apk/' + html.escape(entry["apk"])
        name_escaped = html.escape(entry["name"])
        index_html_file.write(f'<a href="{apk_escaped}">{name_escaped}</a>\n')
    index_html_file.write('</pre>\n</body>\n</html>\n')
