import csv
import importlib.util
import json
import pathlib
import tempfile
import unittest

ROOT=pathlib.Path(__file__).resolve().parents[2]
SPEC=importlib.util.spec_from_file_location("android_performance_suite",ROOT/"tools/android-performance-suite.py")
suite=importlib.util.module_from_spec(SPEC);SPEC.loader.exec_module(suite)

class AndroidPerformanceSuiteTests(unittest.TestCase):
    def fixture(self,root,*,thermal=0,duration=60):
        csv_file=root/"route.csv";fields=sorted(suite.summary.REQUIRED_COLUMNS)
        with csv_file.open("w",newline="",encoding="utf-8") as stream:
            writer=csv.DictWriter(stream,fieldnames=fields);writer.writeheader()
            for _ in range(100):writer.writerow(dict.fromkeys(fields,0)|{"frame_time_us":duration*10000,"fps":100/duration})
        common={"csv":"route.csv","device":"GPU A","driver":"driver A","build":"commit A","route":"route A","seed_sha256":"a"*64,"cache":"warm","thermal_start":0,"thermal_end":thermal,"host_menu":False}
        data={"runs":[common|{"case":"baseline"},common|{"case":"texture-512"}]}
        manifest=root/"results.json";manifest.write_text(json.dumps(data));return manifest,data

    def test_plan_never_changes_existing_files(self):
        with tempfile.TemporaryDirectory() as temporary:
            root=pathlib.Path(temporary);data=suite.plan(root)
            self.assertGreaterEqual(len(data["cases"]),20)
            with self.assertRaises(ValueError):suite.plan(root)

    def test_matching_capture_compares_without_qualifying_device(self):
        with tempfile.TemporaryDirectory() as temporary:
            manifest,_=self.fixture(pathlib.Path(temporary));result=suite.compare(manifest)
            self.assertEqual(result["comparisons"][0]["status"],"within-threshold")
            self.assertEqual(result["qualified_devices"],[])

    def test_short_or_mismatched_runs_are_unqualified(self):
        with tempfile.TemporaryDirectory() as temporary:
            manifest,data=self.fixture(pathlib.Path(temporary),duration=10)
            self.assertEqual(suite.compare(manifest)["comparisons"][0]["status"],"unqualified")
            data["runs"][1]["device"]="Other GPU";manifest.write_text(json.dumps(data))
            self.assertIn("No matching",suite.compare(manifest)["comparisons"][0]["reason"])

    def test_menu_runs_and_unknown_thermal_values_fail(self):
        with tempfile.TemporaryDirectory() as temporary:
            manifest,data=self.fixture(pathlib.Path(temporary));data["runs"][1]["host_menu"]=True;manifest.write_text(json.dumps(data))
            with self.assertRaises(ValueError):suite.compare(manifest)
            data["runs"][1]["host_menu"]=False;data["runs"][1]["thermal_end"]=-1;manifest.write_text(json.dumps(data))
            with self.assertRaises(ValueError):suite.compare(manifest)
