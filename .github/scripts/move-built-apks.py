from pathlib import Path
import shutil

REPO_APK_DIR = Path("repo/apk")

shutil.rmtree(REPO_APK_DIR, ignore_errors=True)
REPO_APK_DIR.mkdir(parents=True, exist_ok=True)

for apk in Path.home().joinpath("apk-artifacts").glob("**/*.apk"):
    apk_name = apk.name.replace("-release.apk", ".apk")

    shutil.move(apk, REPO_APK_DIR.joinpath(apk_name))
