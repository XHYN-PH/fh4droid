"""Validation must be an explicit diagnostic package, never a release default."""
from pathlib import Path
from types import SimpleNamespace
import sys,tempfile,unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import pinyon_android

class AndroidValidationTests(unittest.TestCase):
    def test_normal_package_has_no_validation_library(self):
        self.assertIsNone(pinyon_android.validation_library(SimpleNamespace()))
    def test_validation_cannot_be_included_in_normal_release(self):
        with self.assertRaisesRegex(pinyon_android.AndroidError,'requires --debuggable'):
            pinyon_android.validation_library(SimpleNamespace(validation_layer=Path('layer.so'),debuggable=False))
    def test_wrong_architecture_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            file=Path(folder)/'libVkLayer_khronos_validation.so';header=bytearray(20);header[:6]=b'\x7fELF\x02\x01';header[18:20]=(62).to_bytes(2,'little');file.write_bytes(header)
            with self.assertRaisesRegex(pinyon_android.AndroidError,'ARM64'):
                pinyon_android.validation_library(SimpleNamespace(validation_layer=file,debuggable=True))
            header[18:20]=(183).to_bytes(2,'little');file.write_bytes(header)
            self.assertEqual(pinyon_android.validation_library(SimpleNamespace(validation_layer=file,debuggable=True)),file)
class AndroidBaselineTests(unittest.TestCase):
    def test_package_reports_the_configured_cpu_requirement(self):
        with tempfile.TemporaryDirectory() as folder:
            build_dir = Path(folder)
            cache = build_dir / "CMakeCache.txt"
            for baseline in ("armv8-a","armv8.2-a"):
                cache.write_text(f"PINYON_SHIFT_ANDROID_MARCH:STRING={baseline}\n", encoding="utf-8")
                self.assertEqual(pinyon_android._cpu_baseline(build_dir), baseline)
    def test_unconfigured_build_uses_the_declared_default(self):
        with tempfile.TemporaryDirectory() as folder:
            self.assertEqual(pinyon_android._cpu_baseline(Path(folder)), pinyon_android.CONFIG["cpu_baseline"])

if __name__=='__main__':unittest.main()
