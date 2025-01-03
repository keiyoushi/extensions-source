import sys
import json
from pathlib import Path
import shutil

to_delete = json.loads(sys.argv[1])

for module in to_delete:
    apk_name = f"tachiyomi-{module}-v1.4.*.apk"
    icon_name = f"eu.kanade.tachiyomi.extension.{module}.png"
    
    for p in Path(Path.cwd(), "apk").glob(apk_name):
        p.unlink()
    for p in Path(Path.cwd(), "icon").glob(icon_name):
        p.unlink()
            
shutil.copytree(src="../main/repo/apk", dst=Path.cwd() / "apk")
shutil.copytree(src="../main/repo/icon", dst=Path.cwd() / "icon")

with(open(Path.cwd() / "index.min.json")) as index:
    repo_index = json.load(index)
    
with(open(Path.cwd().parent() / "main" / "repo" / "index.min.json")) as index:
    new_repo_index = json.load(index)
    
for module in to_delete:
    repo_index = [item for item in repo_index if not item["pkg"].endswith(module)]

repo_index.extend(new_repo_index)

repo_index.sort(key=lambda x: x["pkg"])

with open(Path.cwd() / "index.json", "w") as index:
    json.dump(repo_index, index, ensure_ascii=False, indent=2)

with open(Path.cwd() / "index.min.json", "w") as index:
    json.dump(repo_index, index, ensure_ascii=False, separators=(',', ':'))
