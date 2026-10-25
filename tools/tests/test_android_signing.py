"""Release credentials cannot silently fall back to a newly generated local key."""
import pathlib
import sys
import tempfile
import hashlib
import json
import shutil
import zipfile
import unittest
from types import SimpleNamespace
from unittest import mock

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
import pinyon_android


class ReleaseSigningTests(unittest.TestCase):
    def test_bundled_driver_notice_has_one_destination(self):
        with tempfile.TemporaryDirectory() as temp:
            root=pathlib.Path(temp);work=root/"work";binary=work/"drivers/driver";notice=root/"android/drivers/driver"
            binary.mkdir(parents=True);notice.mkdir(parents=True)
            (binary/"libdriver.so").write_bytes(b"driver")
            (binary/"NOTICE.txt").write_text("recovered notice")
            (notice/"NOTICE.txt").write_text("source notice")
            with mock.patch.multiple(pinyon_android,ROOT=root,WORK=work,BUNDLED_DRIVERS=["driver"]):
                files=pinyon_android.bundled_driver_files()
            destinations=[name for _,name in files]
            self.assertEqual(len(destinations),len(set(destinations)))
            selected=[path for path,name in files if name.endswith("/NOTICE.txt")]
            self.assertEqual(len(selected),1)
            self.assertEqual(selected[0].read_text(),"source notice")

    def test_apk_manifest_hashes_the_shipped_library(self):
        with tempfile.TemporaryDirectory() as temp:
            root=pathlib.Path(temp);build=root/"build";build.mkdir();work=root/"work"
            for name in (*pinyon_android.NATIVE_LIBRARIES,*pinyon_android.ADRENOTOOLS_HOOKS,"libc++_shared.so"):
                (build/name).write_bytes(("unstripped-"+name).encode())
            (root/"android/art").mkdir(parents=True);(root/"config").mkdir()
            (root/"android/AndroidManifest.xml").write_text("<manifest />")
            (root/"config/supported-extracted-ms-2505.json").write_text("{}")
            for name in ("release.jks","storepass","keypass"):(root/name).write_text("fixture")
            args=SimpleNamespace(title_update_v4=False,keystore=root/"release.jks",key_alias="release",
                                 storepass_file=root/"storepass",keypass_file=root/"keypass")
            tools=SimpleNamespace(libcxx=build/"libc++_shared.so",android_jar=root/"android.jar",llvm_bin=root/"llvm")
            for name in ("aapt2","javac","d8","strip","zipalign","apksigner"):setattr(tools,name,pathlib.Path(name))
            fail_sign=False
            def run(command,**kwargs):
                command=[str(p) for p in command];program=command[0]
                if program=="aapt2" and command[1]=="link":
                    with zipfile.ZipFile(command[command.index("-o")+1],"w") as apk:apk.writestr("AndroidManifest.xml","fixture")
                elif program=="d8":(pathlib.Path(command[command.index("--output")+1])/"classes.dex").write_bytes(b"fixture dex")
                elif program=="strip":pathlib.Path(command[command.index("-o")+1]).write_bytes(b"stripped-"+pathlib.Path(command[-1]).read_bytes())
                elif program=="zipalign" and "-c" not in command:shutil.copyfile(command[-2],command[-1])
                elif program=="apksigner" and command[1]=="sign":
                    dest=pathlib.Path(command[command.index("--out")+1])
                    if fail_sign:
                        dest.write_bytes(b"partial signing output")
                        raise pinyon_android.AndroidError("simulated signing failure")
                    shutil.copyfile(command[-1],dest)
            with mock.patch.multiple(pinyon_android,ROOT=root,WORK=work), \
                 mock.patch.object(pinyon_android,"build_directory",return_value=build), \
                 mock.patch.object(pinyon_android,"rexsdk_dir",return_value=root/"sdk"), \
                 mock.patch.object(pinyon_android,"release_version",return_value=("test",605)), \
                 mock.patch.object(pinyon_android,"bundled_driver_files",return_value=[]), \
                 mock.patch.object(pinyon_android,"_git",return_value="test"), \
                 mock.patch.object(pinyon_android,"run",side_effect=run), \
                 mock.patch.object(pinyon_android.subprocess,"run",return_value=SimpleNamespace(stdout="LOAD 0x4000\n",returncode=0)), \
                 mock.patch("builtins.print"):
                self.assertEqual(pinyon_android.package(args,tools),0)
                good=(work/"pinyon-shift.apk").read_bytes();fail_sign=True
                with self.assertRaisesRegex(pinyon_android.AndroidError,"signing failure"):
                    pinyon_android.package(args,tools)
                self.assertEqual((work/"pinyon-shift.apk").read_bytes(),good)
            with zipfile.ZipFile(work/"pinyon-shift.apk") as apk:
                shipped=apk.read("lib/arm64-v8a/libmain.so")
                manifest=json.loads(apk.read("assets/pinyon_shift_build.json"))
            self.assertNotEqual(shipped,(build/"libmain.so").read_bytes())
            self.assertEqual(manifest["executable_sha256"],hashlib.sha256(shipped).hexdigest().upper())

    def test_existing_player_builds_keep_local_signing(self):
        self.assertIsNone(pinyon_android._release_signing(SimpleNamespace()))

    def test_partial_signing_options_fail_before_build_or_package(self):
        args = SimpleNamespace(keystore=pathlib.Path("missing.keystore"))
        with mock.patch.object(pinyon_android, "Tools") as tools:
            for operation in (pinyon_android.build, pinyon_android.package):
                with self.assertRaisesRegex(pinyon_android.AndroidError, "together"):
                    operation(args)
            tools.assert_not_called()

    def test_passwords_stay_in_files(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            paths = [root / name for name in ("release.jks", "store-password", "key-password")]
            for path in paths:
                path.write_text("test-only-secret")
            args = SimpleNamespace(keystore=paths[0], key_alias="release",
                                   storepass_file=paths[1], keypass_file=paths[2])
            flags = pinyon_android._release_signing(args)
            self.assertIn("file:" + str(paths[1].resolve()), flags)
            self.assertIn("file:" + str(paths[2].resolve()), flags)
            self.assertNotIn("test-only-secret", " ".join(flags))
            paths[2].unlink()
            with self.assertRaisesRegex(pinyon_android.AndroidError, "must exist"):
                pinyon_android._release_signing(args)


if __name__ == "__main__":
    unittest.main()
