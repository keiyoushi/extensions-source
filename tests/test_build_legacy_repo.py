import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
GENERATOR = PROJECT_ROOT / "scripts" / "build_legacy_repo.py"


class BuildLegacyRepoTests(unittest.TestCase):
    def write_extension(
        self,
        root: Path,
        *,
        package_name: str = "eu.kanade.tachiyomi.extension.all.example",
        extension_lib: str = "1.4",
        content_warning: int = 1,
    ) -> Path:
        build_dir = root / "build"
        release_dir = build_dir / "outputs" / "apk" / "release"
        release_dir.mkdir(parents=True)
        (release_dir / "tachiyomi-all.example-v1.4.7.apk").write_bytes(b"apk")
        metadata = {
            "module": "all.example",
            "theme": None,
            "packageName": package_name,
            "name": "Example",
            "versionCode": 7,
            "versionName": "1.4.7",
            "extensionLib": extension_lib,
            "contentWarning": content_warning,
            "sources": [
                {
                    "id": 42,
                    "name": "Example",
                    "lang": "en",
                    "baseUrl": "https://example.invalid",
                    "mirrorUrls": [],
                },
            ],
        }
        metadata_path = build_dir / "keiyoushi-source-info.json"
        metadata_path.write_text(json.dumps(metadata), encoding="utf-8")
        return metadata_path

    def run_generator(self, source_root: Path, output: Path, *metadata: Path) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [
                sys.executable,
                str(GENERATOR),
                "--source-root",
                str(source_root),
                "--output",
                str(output),
                *map(str, metadata),
            ],
            text=True,
            capture_output=True,
            check=False,
        )

    def test_generates_legacy_catalog_apk_and_icon_layout(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            source_root = root / "source"
            icon = source_root / "src" / "all" / "example" / "res" / "mipmap-xhdpi" / "ic_launcher.png"
            icon.parent.mkdir(parents=True)
            icon.write_bytes(b"icon")
            metadata_path = self.write_extension(root)
            output = root / "repository"

            result = self.run_generator(source_root, output, metadata_path)

            self.assertEqual(result.returncode, 0, result.stderr)
            catalog = json.loads((output / "index.min.json").read_text(encoding="utf-8"))
            self.assertEqual(
                catalog,
                [
                    {
                        "name": "Tachiyomi: Example",
                        "pkg": "eu.kanade.tachiyomi.extension.all.example",
                        "apk": "tachiyomi-all.example-v1.4.7.apk",
                        "lang": "all",
                        "code": 7,
                        "version": "1.4.7",
                        "nsfw": 0,
                        "sources": [
                            {
                                "id": 42,
                                "name": "Example",
                                "lang": "en",
                                "baseUrl": "https://example.invalid",
                            },
                        ],
                    },
                ],
            )
            self.assertEqual(
                (output / "apk" / "tachiyomi-all.example-v1.4.7.apk").read_bytes(),
                b"apk",
            )
            self.assertEqual(
                (output / "icon" / "eu.kanade.tachiyomi.extension.all.example.png").read_bytes(),
                b"icon",
            )

    def test_rejects_metadata_outside_legacy_library_band(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            source_root = root / "source"
            metadata_path = self.write_extension(root, extension_lib="1.6")
            output = root / "repository"

            result = self.run_generator(source_root, output, metadata_path)

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("extensionLib", result.stderr)
            self.assertFalse((output / "index.min.json").exists())


if __name__ == "__main__":
    unittest.main()
