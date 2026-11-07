import pathlib
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from build_ported_apk import safe_extract


class SafeExtractTests(unittest.TestCase):
    def test_extracts_regular_project_files(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            archive = root / "project.zip"
            with zipfile.ZipFile(archive, "w") as zf:
                zf.writestr("settings.gradle.kts", "rootProject.name='x'")
                zf.writestr("app/src/main/AndroidManifest.xml", "<manifest />")
            target = root / "out"
            target.mkdir()
            safe_extract(archive, target)
            self.assertEqual("rootProject.name='x'", (target / "settings.gradle.kts").read_text())
            self.assertTrue((target / "app/src/main/AndroidManifest.xml").is_file())

    def test_rejects_parent_directory_traversal(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            archive = root / "bad.zip"
            with zipfile.ZipFile(archive, "w") as zf:
                zf.writestr("../outside.txt", "bad")
            with self.assertRaises(ValueError):
                safe_extract(archive, root / "out")
            self.assertFalse((root / "outside.txt").exists())

    def test_rejects_duplicate_paths(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            archive = root / "duplicate.zip"
            with zipfile.ZipFile(archive, "w") as zf:
                zf.writestr("app/file.kt", "one")
                zf.writestr("app/file.kt", "two")
            with self.assertRaises(ValueError):
                safe_extract(archive, root / "out")


    def test_rejects_noncanonical_path_aliases(self):
        for unsafe_name in ("app//file.kt", "app/./file.kt", "C:/outside.txt"):
            with self.subTest(name=unsafe_name), tempfile.TemporaryDirectory() as temp:
                root = pathlib.Path(temp)
                archive = root / "alias.zip"
                with zipfile.ZipFile(archive, "w") as zf:
                    zf.writestr(unsafe_name, "bad")
                target = root / "out"
                target.mkdir()
                with self.assertRaises(ValueError):
                    safe_extract(archive, target)

    def test_rejects_backslash_paths(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            archive = root / "backslash.zip"
            with zipfile.ZipFile(archive, "w") as zf:
                zf.writestr("app\\\\file.kt", "bad")
            target = root / "out"
            target.mkdir()
            with self.assertRaises(ValueError):
                safe_extract(archive, target)

    def test_rejects_windows_style_traversal(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            archive = root / "windows.zip"
            with zipfile.ZipFile(archive, "w") as zf:
                zf.writestr(r"..\\outside.txt", "bad")
            with self.assertRaises(ValueError):
                safe_extract(archive, root / "out")


if __name__ == "__main__":
    unittest.main()
