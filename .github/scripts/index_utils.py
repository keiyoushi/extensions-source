import gzip
import html
import json
import re
from pathlib import Path

import index_pb2
from google.protobuf import json_format

PACKAGE_PREFIX = "eu.kanade.tachiyomi.extension."
PKG_NAME_REGEX = re.compile(r"""pkgName\s*=\s*["']([^"']+)["']""")
THEME_REGEX = re.compile(r"""theme\s*=\s*["']([^"']+)["']""")

ICON_BASE_URL = "https://cdn.jsdelivr.net/gh/keiyoushi/extensions-source@main"
ICON_FILE = "res/mipmap-xhdpi/ic_launcher.png"


def get_icon_url(source_dir: Path, module: str, theme: str | None) -> str:
    module_icon = f"src/{module.replace('.', '/')}/{ICON_FILE}"
    if (source_dir / module_icon).exists():
        return f"{ICON_BASE_URL}/{module_icon}"

    if theme:
        theme_icon = f"lib-multisrc/{theme}/{ICON_FILE}"
        if (source_dir / theme_icon).exists():
            return f"{ICON_BASE_URL}/{theme_icon}"

    return f"{ICON_BASE_URL}/core/src/main/{ICON_FILE}"


def get_source_modules(source_dir: Path) -> dict[str, tuple[str, str | None]]:
    """
    returns every extension module present in the source tree as
    {packageName: (module, theme)}, mirroring ExtensionPlugin's applicationId
    """
    modules = {}
    for build_file in sorted(source_dir.glob("src/*/*/build.gradle.kts")):
        lang, extension = build_file.parent.parent.name, build_file.parent.name
        content = build_file.read_text("utf-8")
        pkg_name = PKG_NAME_REGEX.search(content)
        theme = THEME_REGEX.search(content)
        suffix = pkg_name.group(1) if pkg_name else f"{lang}.{extension}"
        modules[PACKAGE_PREFIX + suffix] = (
            f"{lang}.{extension}",
            theme.group(1) if theme else None,
        )
    return modules


def load_index(repo_dir: Path) -> index_pb2.Index:
    with repo_dir.joinpath("index.json").open(encoding="utf-8") as f:
        return json_format.Parse(f.read(), index_pb2.Index())


def load_release_assets(repo_dir: Path) -> dict:
    path = repo_dir / "release-assets.json"
    if not path.exists():
        return {}
    with path.open(encoding="utf-8") as f:
        return json.load(f)


def prune_extensions(
    extensions: list[index_pb2.Extension],
    source_packages: set[str],
) -> list[index_pb2.Extension]:
    """
    drops extensions whose module no longer exists in the source tree, so a
    publish never keeps a deleted extension regardless of which base the
    build matrix was computed from
    """
    kept = []
    for ext in extensions:
        if ext.packageName in source_packages:
            kept.append(ext)
        else:
            print(f"Removing {ext.packageName}: module no longer exists")
    return kept


def write_repo(
    repo_dir: Path,
    extensions: list[index_pb2.Extension],
    release_assets: dict,
) -> None:
    extensions = sorted(extensions, key=lambda ext: ext.packageName)
    packages = {ext.packageName for ext in extensions}
    release_assets = {
        package_name: assets
        for package_name, assets in release_assets.items()
        if package_name in packages
    }

    index = index_pb2.Index(
        name="Keiyoushi",
        badgeLabel="KEI",
        signingKey="9add655a78e96c4ec7a53ef89dccb557cb5d767489fac5e785d671a5a75d4da2",
        contact=index_pb2.Contact(
            website="https://keiyoushi.github.io",
            discord="https://discord.gg/3FbCpdKbdY",
        ),
        extensionList=index_pb2.ExtensionList(extensions=extensions),
    )

    with repo_dir.joinpath("index.json").open("w", encoding="utf-8") as f:
        f.write(
            json_format.MessageToJson(
                index,
                always_print_fields_with_no_presence=False,
                preserving_proto_field_name=True,
            )
        )

    with repo_dir.joinpath("index.pb").open("wb") as f:
        f.write(gzip.compress(index.SerializeToString(deterministic=True), mtime=0))

    with repo_dir.joinpath("release-assets.json").open("w", encoding="utf-8") as f:
        json.dump(release_assets, f, indent=2, sort_keys=True)
        f.write("\n")

    with repo_dir.joinpath("index.html").open("w", encoding="utf-8") as f:
        f.write(
            '<!DOCTYPE html>\n<html>\n<head>\n<meta charset="UTF-8">\n<title>apks</title>\n</head>\n<body>\n<pre>\n'
        )
        for ext in extensions:
            apk_escaped = html.escape(ext.resources.apkUrl)
            name_escaped = html.escape(f"Tachiyomi: {ext.name}")
            f.write(f'<a href="{apk_escaped}">{name_escaped}</a>\n')
        f.write("</pre>\n</body>\n</html>\n")
