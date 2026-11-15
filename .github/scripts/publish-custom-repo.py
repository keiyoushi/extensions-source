import json
import shutil
import sys
from pathlib import Path

SOURCE_DIR = Path(sys.argv[1]).resolve()
REPO_DIR = Path(sys.argv[2]).resolve()

ICON_REL = "res/mipmap-xhdpi/ic_launcher.png"


def find_icon(module: str, theme: str | None) -> Path:
    mod_icon = SOURCE_DIR / f"src/{module.replace('.', '/')}/{ICON_REL}"
    if mod_icon.exists():
        return mod_icon
    if theme:
        theme_icon = SOURCE_DIR / f"lib-multisrc/{theme}/{ICON_REL}"
        if theme_icon.exists():
            return theme_icon
    return SOURCE_DIR / f"core/src/main/{ICON_REL}"


def main() -> None:
    apk_dir = REPO_DIR / "apk"
    icon_dir = REPO_DIR / "icon"
    apk_dir.mkdir(parents=True, exist_ok=True)
    icon_dir.mkdir(parents=True, exist_ok=True)

    index_path = REPO_DIR / "index.min.json"
    if index_path.exists():
        existing = {item["pkg"]: item for item in json.loads(index_path.read_text(encoding="utf-8"))}
    else:
        existing = {}

    found = 0
    for info_file in SOURCE_DIR.glob("src/**/build/keiyoushi-source-info.json"):
        info = json.loads(info_file.read_text(encoding="utf-8"))
        pkg = info["packageName"]
        apk_files = list((info_file.parent / "outputs/apk/release").glob("*.apk"))
        if not apk_files:
            raise FileNotFoundError(f"No release APK found for {pkg} in {info_file.parent}")
        apk_src = apk_files[0]
        shutil.copy2(apk_src, apk_dir / apk_src.name)

        icon_src = find_icon(info["module"], info.get("theme"))
        if icon_src.exists():
            shutil.copy2(icon_src, icon_dir / f"{pkg}.png")

        sources = [
            {
                "name": s["name"],
                "lang": s["lang"],
                "id": str(s["id"]),
                "baseUrl": s["baseUrl"],
            }
            for s in info["sources"]
        ]
        langs = {s["lang"] for s in sources}
        top_lang = next(iter(langs)) if len(langs) == 1 else "all"
        cw = str(info.get("contentWarning", "SAFE")).upper()
        nsfw = 0 if "SAFE" in cw or cw in ("0", "FALSE", "NONE", "") else 1

        existing[pkg] = {
            "name": f"Tachiyomi: {info['name']}",
            "pkg": pkg,
            "apk": apk_src.name,
            "lang": top_lang,
            "code": int(info["versionCode"]),
            "version": info["versionName"],
            "nsfw": nsfw,
            "sources": sources,
        }
        found += 1

    if found == 0:
        raise RuntimeError("No keiyoushi-source-info.json files found after build")

    entries = sorted(existing.values(), key=lambda x: x["pkg"])
    index_path.write_text(json.dumps(entries, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"Published {found} extension(s); total in index: {len(entries)}")


if __name__ == "__main__":
    main()
