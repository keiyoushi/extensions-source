import gzip
import json
import os
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import index_pb2  # noqa: E402
from google.protobuf import json_format  # noqa: E402

SOURCE_DIR = Path(sys.argv[1]).resolve()
REPO_DIR = Path(sys.argv[2]).resolve()
REPO_SLUG = os.environ.get("GITHUB_REPOSITORY", "MamiZ35/extensions-source")
SIGNING_CERT_SHA256 = os.environ.get("SIGNING_CERT_SHA256", "")

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

    # Load existing protobuf index if present so previous extensions are preserved
    remote_extensions: dict[str, index_pb2.Extension] = {}
    index_json_path = REPO_DIR / "index.json"
    if index_json_path.exists():
        remote_proto = json_format.Parse(index_json_path.read_text(encoding="utf-8"), index_pb2.Index())
        for ext in remote_proto.extensionList.extensions:
            remote_extensions[ext.packageName] = ext

    # Also load existing index.min.json for legacy compatibility
    min_index_path = REPO_DIR / "index.min.json"
    if min_index_path.exists():
        existing_min = {item["pkg"]: item for item in json.loads(min_index_path.read_text(encoding="utf-8"))}
    else:
        existing_min = {}

    raw_base = f"https://raw.githubusercontent.com/{REPO_SLUG}/repo"
    found = 0
    for info_file in SOURCE_DIR.glob("src/**/build/keiyoushi-source-info.json"):
        info = json.loads(info_file.read_text(encoding="utf-8"))
        pkg = info["packageName"]
        apk_src = next((info_file.parent / "outputs/apk/release").glob("*.apk"), None)
        if apk_src is None:
            raise FileNotFoundError(f"No release APK found for {pkg} in {info_file.parent}")
        shutil.copy2(apk_src, apk_dir / apk_src.name)

        jar_src = next((info_file.parent / "outputs/jar/release").glob("*.jar"), None)
        jar_url = ""
        if jar_src is not None:
            shutil.copy2(jar_src, apk_dir / jar_src.name)
            jar_url = f"{raw_base}/apk/{jar_src.name}"

        icon_src = find_icon(info["module"], info.get("theme"))
        if icon_src.exists():
            shutil.copy2(icon_src, icon_dir / f"{pkg}.png")

        pb_ext = index_pb2.Extension(
            name=info["name"],
            packageName=pkg,
            resources=index_pb2.Resources(
                apkUrl=f"{raw_base}/apk/{apk_src.name}",
                iconUrl=f"{raw_base}/icon/{pkg}.png",
                jarUrl=jar_url,
            ),
            extensionLib=info["extensionLib"],
            versionCode=int(info["versionCode"]),
            versionName=info["versionName"],
            contentWarning=info["contentWarning"],
            sources=[
                index_pb2.Source(
                    id=int(s["id"]),
                    name=s["name"],
                    language=s["lang"],
                    homeUrl=s["baseUrl"],
                    mirrorUrls=s.get("mirrorUrls", []),
                )
                for s in info["sources"]
            ],
        )
        remote_extensions[pkg] = pb_ext

        sources_min = [
            {
                "name": s["name"],
                "lang": s["lang"],
                "id": str(s["id"]),
                "baseUrl": s["baseUrl"],
            }
            for s in info["sources"]
        ]
        langs = {s["lang"] for s in sources_min}
        top_lang = next(iter(langs)) if len(langs) == 1 else "all"
        cw = str(info.get("contentWarning", "1")).upper()
        nsfw = 1 if cw in ("2", "3", "CONTENT_WARNING_MIXED", "CONTENT_WARNING_NSFW", "NSFW", "MIXED", "TRUE") else 0

        existing_min[pkg] = {
            "name": f"Tachiyomi: {info['name']}",
            "pkg": pkg,
            "apk": apk_src.name,
            "lang": top_lang,
            "code": int(info["versionCode"]),
            "version": info["versionName"],
            "nsfw": nsfw,
            "sources": sources_min,
        }
        found += 1

    if found == 0:
        raise RuntimeError("No keiyoushi-source-info.json files found after build")

    final_extensions = sorted(remote_extensions.values(), key=lambda e: e.packageName)
    index = index_pb2.Index(
        name=REPO_SLUG.split("/")[0],
        badgeLabel="CST",
        signingKey=SIGNING_CERT_SHA256,
        contact=index_pb2.Contact(
            website=f"https://github.com/{REPO_SLUG}",
        ),
        extensionList=index_pb2.ExtensionList(extensions=final_extensions),
    )

    index_json_path.write_text(
        json_format.MessageToJson(
            index,
            always_print_fields_with_no_presence=False,
            preserving_proto_field_name=True,
        ),
        encoding="utf-8",
    )
    (REPO_DIR / "index.pb").write_bytes(
        gzip.compress(index.SerializeToString(deterministic=True), mtime=0)
    )

    entries_min = sorted(existing_min.values(), key=lambda x: x["pkg"])
    min_index_path.write_text(
        json.dumps(entries_min, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    print(f"Published {found} extension(s); total in index: {len(final_extensions)}")


if __name__ == "__main__":
    main()
