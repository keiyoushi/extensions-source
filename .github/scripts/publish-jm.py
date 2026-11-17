"""
Publishes the extensions this fork maintains (MODULES) as its own extension repo.

usage: publish-jm.py <source root> <repo branch dir> <signing key sha256>
env: GITHUB_REPOSITORY, GITHUB_SHA, GITHUB_RUN_NUMBER, GH_TOKEN (for the gh CLI)
"""

import gzip
import json
import os
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import index_pb2  # noqa: E402
from google.protobuf import json_format  # noqa: E402

MODULES = ["src/zh/jinmantiantangmod", "src/zh/picacomicmod"]

source_root = Path(sys.argv[1])
repo_dir = Path(sys.argv[2])
signing_key = sys.argv[3]
repository = os.environ["GITHUB_REPOSITORY"]
sha_short = os.environ["GITHUB_SHA"][:7]

tag = f"ext-{sha_short}-{os.environ['GITHUB_RUN_NUMBER']}"
release_url = f"https://github.com/{repository}/releases/download/{tag}"
files: list[str] = []
extensions = []
for module in MODULES:
    build_dir = source_root / module / "build"
    info = json.loads((build_dir / "keiyoushi-source-info.json").read_text(encoding="utf-8"))
    apk = next((build_dir / "outputs/apk/release").glob("*.apk"))
    jar = next((build_dir / "outputs/jar/release").glob("*.jar"))
    files += [str(apk), str(jar)]
    extensions.append(
        index_pb2.Extension(
            name=info["name"],
            packageName=info["packageName"],
            resources=index_pb2.Resources(
                apkUrl=f"{release_url}/{apk.name}",
                jarUrl=f"{release_url}/{jar.name}",
                iconUrl=f"https://raw.githubusercontent.com/{repository}/main/{module}/res/mipmap-xhdpi/ic_launcher.png",
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
    )

subprocess.run(
    [
        "gh", "release", "create", tag, *files,
        "--repo", repository,
        "--title", tag,
        "--notes", f"Built from {os.environ['GITHUB_SHA']}",
    ],
    check=True,
)

owner = repository.split("/")[0]
index = index_pb2.Index(
    name=f"{owner} 自用扩展",
    badgeLabel="MOD",
    signingKey=signing_key,
    contact=index_pb2.Contact(website=f"https://github.com/{repository}"),
    extensionList=index_pb2.ExtensionList(extensions=extensions),
)

repo_dir.joinpath("index.pb").write_bytes(
    gzip.compress(index.SerializeToString(deterministic=True), mtime=0)
)
repo_dir.joinpath("index.json").write_text(
    json_format.MessageToJson(index, preserving_proto_field_name=True),
    encoding="utf-8",
)
index_url = f"https://github.com/{repository}/raw/repo/index.pb"
repo_dir.joinpath("repo.json").write_text(
    json.dumps(
        {
            "index_v2": index_url,
            "meta": {
                "name": index.name,
                "website": index.contact.website,
                "signingKeyFingerprint": signing_key,
            },
        },
        ensure_ascii=False,
        indent=2,
    )
    + "\n",
    encoding="utf-8",
)
repo_dir.joinpath("README.md").write_text(
    f"Add this URL in the app as an extension repo:\n\n{index_url}\n",
    encoding="utf-8",
)
for ext in extensions:
    print(f"Published {ext.packageName} {ext.versionName} as {tag}")
