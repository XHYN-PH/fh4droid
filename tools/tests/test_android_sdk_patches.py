"""Separately maintained driver patches must survive shader regeneration."""
import pathlib
import runpy
import tempfile
import unittest
from unittest import mock

ROOT = pathlib.Path(__file__).resolve().parents[2]


class AndroidSdkPatchTests(unittest.TestCase):
    def test_regeneration_keeps_driver_changes_separate(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            script = root / "tools/update-android-sdk-patch.py"
            script.parent.mkdir()
            script.write_bytes((ROOT / "tools/update-android-sdk-patch.py").read_bytes())
            patches = root / "cmake/patches"
            patches.mkdir(parents=True)
            driver = patches / "shiftglue-fh4droid-z-driver-preflight.patch"
            driver_bytes = b"diff --git a/src/ui/driver.cpp b/src/ui/driver.cpp\n" \
                b"diff --git a/thirdparty/loader/hook.cpp b/thirdparty/loader/hook.cpp\n"
            driver.write_bytes(driver_bytes)
            validation=patches/"shiftglue-fh4droid-zz-validation.patch"
            validation_bytes=b"diff --git a/src/validation.cpp b/src/validation.cpp\n+validation\n"
            validation.write_bytes(validation_bytes)
            runtime = b"diff --git a/src/runtime.cpp b/src/runtime.cpp\n+runtime\n"
            shader = b"diff --git a/src/graphics/shaders/vulkan_spirv/a.h b/src/graphics/shaders/vulkan_spirv/a.h\n+shader\n"
            diff = validation_bytes + runtime + b"diff --git a/src/ui/driver.cpp b/src/ui/driver.cpp\n+driver\n" \
                + b"diff --git a/thirdparty/loader b/thirdparty/loader\n+dirty\n" + shader
            with mock.patch("subprocess.check_output", side_effect=[
                "79d3e6fae4b79e00def48a595c80f0eaa47fd2b6\n", diff
            ]), mock.patch("builtins.print"):
                runpy.run_path(str(script), run_name="__main__")
            self.assertEqual(validation.read_bytes(),validation_bytes)
            self.assertEqual(driver.read_bytes(), driver_bytes)
            self.assertEqual((patches / "shiftglue-fh4droid-runtime.patch").read_bytes(), runtime)
            self.assertEqual((patches / "shiftglue-fh4droid-spirv-1.patch").read_bytes(), shader)


if __name__ == "__main__":
    unittest.main()
