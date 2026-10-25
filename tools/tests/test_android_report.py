"""Checks timing windows and audio correlation with deliberately different frames."""
import importlib.util
import io
import json
from pathlib import Path
import unittest
import zipfile
spec=importlib.util.spec_from_file_location('android_report',Path(__file__).resolve().parents[1]/'analyze-android-report.py')
report=importlib.util.module_from_spec(spec);spec.loader.exec_module(report)

class AndroidReportTest(unittest.TestCase):
    def test_older_csv_is_not_assigned_to_latest_launch_settings(self):
        data = io.BytesIO()
        with zipfile.ZipFile(data, "w") as archive:
            for session in ("20261022T023322Z-p31093", "20261022T023649Z-p5329"):
                archive.writestr(session + ".jsonl", json.dumps(dict(event="process.start", session=session)))
            archive.writestr("logs/20261022T023322Z-p31093.perf.csv", "frame_time_us\n16666\n")
        with zipfile.ZipFile(data) as archive:
            result, = report.analyse_zip(archive)
        self.assertFalse(result["matches_latest_session"])
        self.assertEqual(result["latest_report_session"], "20261022T023649Z-p5329")
        self.assertIn("Historical", result["session_warning"])

    def test_matching_capture_is_identified_without_false_warning(self):
        data = io.BytesIO()
        session = "20261022T023322Z-p31093"
        with zipfile.ZipFile(data, "w") as archive:
            archive.writestr(session + ".jsonl", json.dumps(dict(event="process.start", session=session)))
            archive.writestr(session + ".perf.csv", "frame_time_us\n16666\n")
        with zipfile.ZipFile(data) as archive:
            result, = report.analyse_zip(archive)
        self.assertTrue(result["matches_latest_session"])
        self.assertNotIn("session_warning", result)

    def test_audio_overlap_does_not_turn_warnings_into_causation(self):
        text='frame_time_us,xma_no_progress_stalls\n16000,0\n50000,1\n60000,0\n16000,1\n'
        result=report.analyse(text,target_fps=60)
        self.assertEqual(result['slow_frames'],2)
        self.assertEqual(result['frames_with_audio_stalls'],2)
        self.assertEqual(result['slow_frames_with_audio_stalls'],1)
        self.assertAlmostEqual(result['captured_seconds'],.142)
    def test_window_excludes_startup_and_handles_missing_audio_columns(self):
        text='frame_time_us,draw_calls\n1000000,0\n16000,2\n18000,2\n'
        result=report.analyse(text,skip_seconds=1,duration=.02)
        self.assertEqual(result['frames'],2)
        self.assertAlmostEqual(result['median_ms'],17)
        self.assertFalse(result['audio_counters_available'])
    def test_missing_header_is_not_silently_treated_as_a_benchmark(self):
        with self.assertRaises(ValueError):report.analyse('16000,0\n17000,0\n')
    def test_partial_records_do_not_become_complete_frames(self):
        result=report.analyse('frame_time_us,draw_calls\n16000,2\n17000\n18000,3,unexpected\n')
        self.assertEqual(result['frames'],1)
        self.assertEqual(result['incomplete_rows_ignored'],2)
    def test_invalid_comparison_windows_are_rejected(self):
        for options in ({'target_fps':0},{'skip_seconds':-1},{'duration':float('nan')}):
            with self.assertRaises(ValueError):report.analyse('frame_time_us\n16000\n',**options)
    def test_report_index_preserves_build_identity_without_start_event(self):
        data=io.BytesIO();session='20261025T005207Z-p17867';name='logs/history/1-'+session+'.perf.csv'
        with zipfile.ZipFile(data,'w') as archive:
            archive.writestr(name,'frame_time_us\n33333\n')
            archive.writestr('report-index.json',json.dumps({'latest_session':session,'app_source_commit':'new-build','files':[{'entry':name,'source_commit':'old-build','tail_truncated':True}]}))
        with zipfile.ZipFile(data) as archive:result,=report.analyse_zip(archive)
        self.assertTrue(result['matches_latest_session'])
        self.assertFalse(result['matches_app_build'])
        self.assertTrue(result['tail_truncated'])
