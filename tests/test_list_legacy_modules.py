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

    def test_lists_only_extensions_in_legacy_library_band(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            source_root = Path(temporary_directory)
            self.write_module(source_root, "en", "legacy", "1.4")
            self.write_module(source_root, "all", "shared", "1.5")
            self.write_module(source_root, "en", "modern", "1.6")

            result = subprocess.run(
                [sys.executable, str(SELECTOR), "--source-root", str(source_root)],
                text=True,
                capture_output=True,
                check=False,
            )

            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(
                result.stdout.splitlines(),
                [
                    ":src:all:shared:assembleRelease",
                    ":src:en:legacy:assembleRelease",
                ],
            )


if __name__ == "__main__":
    unittest.main()
