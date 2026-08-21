import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
SELECTOR = PROJECT_ROOT / "scripts" / "list_legacy_modules.py"


class ListLegacyModulesTests(unittest.TestCase):
    def write_module(self, source_root: Path, language: str, name: str, library_version: str) -> None:
        module = source_root / "src" / language / name
        module.mkdir(parents=True)
        (module / "build.gradle.kts").write_text(
            "keiyoushi {\n"
            f'    libVersion = "{library_version}"\n'
            "}\n",
            encoding="utf-8",
        )

    def write_selection(self, source_root: Path, *modules: str) -> None:
        (source_root / "legacy-modules.txt").write_text("\n".join(modules) + "\n", encoding="utf-8")

    def test_lists_only_verified_runtime_checked_modules(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            source_root = Path(temporary_directory)
            self.write_module(source_root, "en", "legacy", "1.4")
            self.write_module(source_root, "all", "unverified", "1.4")
            self.write_module(source_root, "all", "unsupported", "1.5")
            self.write_selection(source_root, "en/legacy")

            result = subprocess.run(
                [sys.executable, str(SELECTOR), "--source-root", str(source_root)],
                text=True,
                capture_output=True,
                check=False,
            )

            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(
                result.stdout.splitlines(),
                [":src:en:legacy:assembleRelease"],
            )

    def test_rejects_verified_module_without_legacy_library(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            source_root = Path(temporary_directory)
            self.write_module(source_root, "all", "unsupported", "1.5")
            self.write_selection(source_root, "all/unsupported")

            result = subprocess.run(
                [sys.executable, str(SELECTOR), "--source-root", str(source_root)],
                text=True,
                capture_output=True,
                check=False,
            )

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("extension library 1.4", result.stderr)

    def test_rejects_missing_module_selection(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            source_root = Path(temporary_directory)

            result = subprocess.run(
                [sys.executable, str(SELECTOR), "--source-root", str(source_root)],
                text=True,
                capture_output=True,
                check=False,
            )

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("missing verified legacy module list", result.stderr)

    def test_rejects_malformed_module_selection(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            source_root = Path(temporary_directory)
            self.write_selection(source_root, "en/not-valid")

            result = subprocess.run(
                [sys.executable, str(SELECTOR), "--source-root", str(source_root)],
                text=True,
                capture_output=True,
                check=False,
            )

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("invalid legacy module", result.stderr)

    def test_rejects_duplicate_module_selection(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            source_root = Path(temporary_directory)
            self.write_module(source_root, "en", "legacy", "1.4")
            self.write_selection(source_root, "en/legacy", "en/legacy")

            result = subprocess.run(
                [sys.executable, str(SELECTOR), "--source-root", str(source_root)],
                text=True,
                capture_output=True,
                check=False,
            )

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("duplicate module", result.stderr)


if __name__ == "__main__":
    unittest.main()
