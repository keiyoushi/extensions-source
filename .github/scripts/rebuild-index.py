"""
Rebuilds the published index from local files only: the checked-out `repo`
branch (working directory) and this source tree. Nothing is built or uploaded.

- drops extensions whose module no longer exists in the source tree
- relinks icon URLs to the current source tree
- keeps every other entry, including its apk/jar URLs, untouched
"""

import sys
from pathlib import Path

from index_utils import (
    get_icon_url,
    get_source_modules,
    load_index,
    load_release_assets,
    prune_extensions,
    write_repo,
)

REPO_DIR = Path.cwd()
SOURCE_DIR = Path(__file__).resolve().parents[2]


def main() -> None:
    source_modules = get_source_modules(SOURCE_DIR)
    release_assets = load_release_assets(REPO_DIR)
    extensions = prune_extensions(
        list(load_index(REPO_DIR).extensionList.extensions),
        set(source_modules),
    )

    unpublished = sorted(set(source_modules) - {ext.packageName for ext in extensions})
    for package_name in unpublished:
        print(f"Not published yet (needs a build): {package_name}")

    relinked = 0
    for ext in extensions:
        module, theme = source_modules[ext.packageName]
        icon_url = get_icon_url(SOURCE_DIR, module, theme)
        if ext.resources.iconUrl != icon_url:
            print(f"Relinking icon of {ext.packageName}: {icon_url}")
            ext.resources.iconUrl = icon_url
            relinked += 1

        assets = release_assets.get(ext.packageName, {})
        for kind, url in (("apk", ext.resources.apkUrl), ("jar", ext.resources.jarUrl)):
            name = assets.get(kind, {}).get("name")
            if not url or url.rsplit("/", 1)[-1] != name:
                print(
                    f"WARNING: {ext.packageName} {kind} url '{url}' doesn't match "
                    f"release asset '{name}'; rebuild it to fix",
                    file=sys.stderr,
                )

    write_repo(REPO_DIR, extensions, release_assets)
    print(f"Index rebuilt: {len(extensions)} extensions, {relinked} icons relinked")


if __name__ == "__main__":
    main()
