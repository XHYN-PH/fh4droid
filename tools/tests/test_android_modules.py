"""Prevent packaging a main-only image that cannot start the guest modules."""
import pathlib
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest import mock
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]))
import android_module_check
ROOT=pathlib.Path(__file__).resolve().parents[2]

@unittest.skipUnless(shutil.which('cmake'),'CMake is required')
class GeneratedModuleTests(unittest.TestCase):
    def configure(self,broken=None):
        with tempfile.TemporaryDirectory() as temp:
            root=pathlib.Path(temp);generated=root/'generated/default';generated.mkdir(parents=True)
            init='.register_modules = RegisterRecompiledModules'
            registry='speechfacade_default.xex pinyon_shift_SpeechFacade_default xmediafacade_default.xex pinyon_shift_XMediaFacade_default'
            sources='set(GENERATED_SOURCES module_registry.cpp)'
            if broken=='callback':init='.register_modules = nullptr'
            if broken=='speech':registry=registry.replace('speechfacade_default.xex','missing.xex')
            if broken=='xmedia':registry=registry.replace('pinyon_shift_XMediaFacade_default','missing_library')
            if broken=='sources':sources='set(GENERATED_SOURCES pinyon_shift_init.cpp)'
            (generated/'pinyon_shift_init.cpp').write_text(init)
            (generated/'sources.cmake').write_text(sources)
            if broken!='registry':(generated/'module_registry.cpp').write_text(registry)
            script=(ROOT/'cmake/PinyonShiftGeneratedModules.cmake').as_posix()
            (root/'CMakeLists.txt').write_text(f'cmake_minimum_required(VERSION 3.25)\nproject(module_check NONE)\ninclude("{script}")\npinyon_shift_verify_generated_modules("{(root/"generated").as_posix()}" "{generated.as_posix()}")\n')
            return subprocess.run(['cmake','-S',str(root),'-B',str(root/'build')],text=True,capture_output=True)
    def test_complete_registry_configures(self):
        result=self.configure();self.assertEqual(result.returncode,0,result.stderr)
    def test_missing_or_disconnected_registry_never_configures(self):
        for broken in ('registry','callback','sources','speech','xmedia'):
            with self.subTest(broken=broken):
                result=self.configure(broken);self.assertNotEqual(result.returncode,0);self.assertIn('CMake Error',result.stderr)

class PackagedModuleTests(unittest.TestCase):
    libraries=[pathlib.Path(x) for x in ('libmain.so','libpinyon_shift_SpeechFacade_default.so','libpinyon_shift_XMediaFacade_default.so')]
    symbols='1: 0000000000001000 64 OBJECT GLOBAL DEFAULT 27 PPCImageConfig\n2: 0000000000003000 50 FUNC GLOBAL DEFAULT 14 _Z25RegisterRecompiledModulesPN3rex6system11KernelStateE\n'
    exports='00000010 T ReXModule_Register\n00000020 T ReXModule_GetImageInfo\n'
    def check(self,outputs,libraries=None):
        with mock.patch.object(android_module_check,'inspect',side_effect=outputs):
            android_module_check.verify(libraries if libraries is not None else self.libraries,pathlib.Path('readelf'),pathlib.Path('nm'))
    def test_registry_and_both_exports_pass(self):
        self.check([self.symbols,self.exports,self.exports])
    def test_missing_registry_symbol_fails(self):
        with self.assertRaisesRegex(ValueError,'registry is missing'):self.check(['1: 1000 64 OBJECT GLOBAL DEFAULT 27 PPCImageConfig'])
    def test_missing_image_config_fails(self):
        with self.assertRaisesRegex(ValueError,'registry is missing'):self.check(['2: 3000 50 FUNC GLOBAL DEFAULT 14 RegisterRecompiledModules'])
    def test_missing_facade_entry_export_fails(self):
        with self.assertRaisesRegex(ValueError,'exports are missing'):self.check([self.symbols,self.exports,'00000010 T ReXModule_Register'])
    def test_missing_facade_library_fails(self):
        with self.assertRaisesRegex(ValueError,'library is missing'):self.check([self.symbols,self.exports],self.libraries[:2])

if __name__=='__main__':unittest.main()
