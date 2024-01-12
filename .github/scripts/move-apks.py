from pathlib import Path
import shutil

REPO_APK_DIR = Path("repo/apk")

try:
    shutil.rmtree(REPO_APK_DIR)
except FileNotFoundError:
    pass

REPO_APK_DIR.mkdir(parents=True, exist_ok=True)

for apk in (Path.home() / "apk-artifacts").glob("**/*.apk"):
    apk_name = apk.name.replace("-release.apk", ".apk")

    shutil.move(apk, REPO_APK_DIR / apk_name)
