import json
import subprocess
import sys
import tempfile
import threading
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
        release_dir.mkdir(parents=True, exist_ok=True)
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

    def test_replaces_existing_catalog_without_removing_output(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            source_root = root / "source"
            icon = source_root / "src" / "all" / "example" / "res" / "mipmap-xhdpi" / "ic_launcher.png"
            icon.parent.mkdir(parents=True)
            icon.write_bytes(b"icon")
            metadata_path = self.write_extension(root)
            output = root / "repository"

            first_result = self.run_generator(source_root, output, metadata_path)
            self.assertEqual(first_result.returncode, 0, first_result.stderr)
            first_catalog = (output / "index.min.json").read_text(encoding="utf-8")

            metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
            metadata["versionCode"] = 8
            metadata["versionName"] = "1.4.8"
            metadata_path.write_text(json.dumps(metadata), encoding="utf-8")
            release_directory = metadata_path.parent / "outputs" / "apk" / "release"
            (release_directory / "tachiyomi-all.example-v1.4.7.apk").unlink()
            (release_directory / "tachiyomi-all.example-v1.4.8.apk").write_bytes(b"updated apk")

            second_result = self.run_generator(source_root, output, metadata_path)

            self.assertEqual(second_result.returncode, 0, second_result.stderr)
            self.assertEqual(json.loads((output / "index.min.json").read_text(encoding="utf-8"))[0]["code"], 8)
            self.assertEqual((root / ".repository.previous" / "index.min.json").read_text(encoding="utf-8"), first_catalog)

    def test_preserves_existing_catalog_when_preparation_fails(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            source_root = root / "source"
            icon = source_root / "src" / "all" / "example" / "res" / "mipmap-xhdpi" / "ic_launcher.png"
            icon.parent.mkdir(parents=True)
            icon.write_bytes(b"icon")
            metadata_path = self.write_extension(root)
            output = root / "repository"

            first_result = self.run_generator(source_root, output, metadata_path)
            self.assertEqual(first_result.returncode, 0, first_result.stderr)
            first_catalog = (output / "index.min.json").read_text(encoding="utf-8")

            release_directory = metadata_path.parent / "outputs" / "apk" / "release"
            (release_directory / "tachiyomi-all.example-v1.4.7.apk").unlink()
            failed_result = self.run_generator(source_root, output, metadata_path)

            self.assertNotEqual(failed_result.returncode, 0)
            self.assertEqual((output / "index.min.json").read_text(encoding="utf-8"), first_catalog)

    def test_keeps_catalog_available_to_concurrent_readers(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            source_root = root / "source"
            icon = source_root / "src" / "all" / "example" / "res" / "mipmap-xhdpi" / "ic_launcher.png"
            icon.parent.mkdir(parents=True)
            icon.write_bytes(b"icon")
            metadata_path = self.write_extension(root)
            output = root / "repository"

            initial_result = self.run_generator(source_root, output, metadata_path)
            self.assertEqual(initial_result.returncode, 0, initial_result.stderr)
            errors = []

            def publish_repeatedly() -> None:
                for _ in range(32):
                    result = self.run_generator(source_root, output, metadata_path)
                    if result.returncode != 0:
                        errors.append(result.stderr)

            publisher = threading.Thread(target=publish_repeatedly)
            publisher.start()
            while publisher.is_alive():
                try:
                    catalog = json.loads((output / "index.min.json").read_text(encoding="utf-8"))
                    if catalog[0]["pkg"] != "eu.kanade.tachiyomi.extension.all.example":
                        errors.append("reader received an incomplete catalog")
                except (IndexError, OSError, json.JSONDecodeError) as error:
                    errors.append(str(error))
            publisher.join()

            self.assertEqual(errors, [])

    def test_rejects_package_names_that_escape_catalog_assets(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            output = root / "repository"

            for package_name in ("../../outside", "/tmp/outside", "eu.kanade.tachiyomi.extension.all.different"):
                metadata_path = self.write_extension(root, package_name=package_name)
                result = self.run_generator(root / "source", output, metadata_path)

                self.assertNotEqual(result.returncode, 0)
                self.assertIn("packageName", result.stderr)
                self.assertFalse((root / "outside").exists())

    def test_rejects_extension_library_without_a_build_mapping(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            source_root = root / "source"
            output = root / "repository"

            for extension_lib in ("1.5", "1.6"):
                metadata_path = self.write_extension(root, extension_lib=extension_lib)
                result = self.run_generator(source_root, output, metadata_path)

                self.assertNotEqual(result.returncode, 0)
                self.assertIn("extensionLib", result.stderr)
                self.assertFalse((output / "index.min.json").exists())

    def test_rejects_a_release_apk_that_does_not_match_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            source_root = root / "source"
            icon = source_root / "src" / "all" / "example" / "res" / "mipmap-xhdpi" / "ic_launcher.png"
            icon.parent.mkdir(parents=True)
            icon.write_bytes(b"icon")
            metadata_path = self.write_extension(root)
            release_directory = metadata_path.parent / "outputs" / "apk" / "release"
            (release_directory / "tachiyomi-all.example-v1.4.7.apk").unlink()
            (release_directory / "stale.apk").write_bytes(b"stale")

            result = self.run_generator(source_root, root / "repository", metadata_path)

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("expected release APK", result.stderr)

    def test_rejects_boolean_values_for_numeric_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            metadata_path = self.write_extension(root)
            metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
            metadata["sources"][0]["id"] = True
            metadata_path.write_text(json.dumps(metadata), encoding="utf-8")

            result = self.run_generator(root / "source", root / "repository", metadata_path)

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("must be an integer", result.stderr)

    def test_uses_the_multisrc_theme_icon_when_module_icon_is_absent(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            source_root = root / "source"
            theme_icon = source_root / "lib-multisrc" / "example-theme" / "res" / "mipmap-xhdpi" / "ic_launcher.png"
            theme_icon.parent.mkdir(parents=True)
            theme_icon.write_bytes(b"theme icon")
            metadata_path = self.write_extension(root)
            metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
            metadata["theme"] = "example-theme"
            metadata_path.write_text(json.dumps(metadata), encoding="utf-8")
            output = root / "repository"

            result = self.run_generator(source_root, output, metadata_path)

            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(
                (output / "icon" / "eu.kanade.tachiyomi.extension.all.example.png").read_bytes(),
                b"theme icon",
            )


if __name__ == "__main__":
    unittest.main()
