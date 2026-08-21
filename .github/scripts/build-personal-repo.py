import json
import os
import shutil
from pathlib import Path

# Collect release APKs + keiyoushi-source-info.json emitted by assembleRelease,
# copy APKs and generate a classic index.min.json for Mihon-style apps.

out_dir = Path("personal-repo")
apk_dir = out_dir / "apk"
apk_dir.mkdir(parents=True, exist_ok=True)
icon_dir = out_dir / "icon"
icon_dir.mkdir(parents=True, exist_ok=True)

entries = []
for info_file in sorted(Path(".").glob("src/**/build/keiyoushi-source-info.json")):
    info = json.loads(info_file.read_text(encoding="utf-8"))
    apk = next((info_file.parent / "outputs/apk/release").glob("*.apk"), None)
    if apk is None:
        print(f"WARNING: no release apk for {info.get('packageName')}", flush=True)
        continue
    shutil.copy2(apk, apk_dir / apk.name)
    module_dir = info_file.parent.parent
    icon = next(
        (
            module_dir / "res" / f"mipmap-{d}" / "ic_launcher.png"
            for d in ("xhdpi", "xxhdpi", "xxxhdpi", "hdpi", "mdpi")
            if (module_dir / "res" / f"mipmap-{d}" / "ic_launcher.png").exists()
        ),
        None,
    )
    if icon is not None:
        shutil.copy2(icon, icon_dir / f"{info['packageName']}.png")
    else:
        print(f"WARNING: no icon for {info.get('packageName')}", flush=True)
    lang = info["module"].split(".")[0]
    entries.append(
        {
            "name": info["name"],
            "pkg": info["packageName"],
            "apk": apk.name,
            "lang": lang,
            "code": info["versionCode"],
            "version": info["versionName"],
            # contentWarning: 1=SAFE, 2=MIXED, 3=NSFW
            "nsfw": 1 if info.get("contentWarning") == 3 else 0,
            "sources": [
                {
                    "name": s["name"],
                    "lang": s["lang"],
                    "id": str(s["id"]),
                    "baseUrl": s["baseUrl"],
                }
                for s in info.get("sources", [])
            ],
        }
    )

entries.sort(key=lambda e: e["pkg"])
(out_dir / "index.min.json").write_text(
    json.dumps(entries, separators=(",", ":"), ensure_ascii=False), encoding="utf-8"
)

# repo.json is required by newer Mihon versions when adding a legacy store.
fingerprint = os.environ.get("SIGNING_FINGERPRINT", "").strip()
repo_meta = {
    "index_v2": None,
    "meta": {
        "name": "TeTKiK",
        "website": "https://github.com/TeTKiK15/uzantilar",
        "signingKeyFingerprint": fingerprint,
    },
}
(out_dir / "repo.json").write_text(
    json.dumps(repo_meta, separators=(",", ":"), ensure_ascii=False), encoding="utf-8"
)
print(f"Generated index with {len(entries)} extensions")
