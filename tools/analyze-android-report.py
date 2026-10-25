#!/usr/bin/env python3
"""Summarise a captured FH4Droid CSV or problem-report ZIP.

This cannot distinguish menus from driving. Select an explicit time window for
same-scene comparisons. Audio overlap is a correlation, not a diagnosis.
"""
from __future__ import annotations
import argparse
import csv
import io
import json
import math
import re
from pathlib import Path
import statistics
import zipfile

AUDIO = ("xma_no_space_stalls", "xma_no_progress_stalls", "xma_stall_recoveries")
SESSION = re.compile(r"\d{8}T\d{6}Z-p\d+")

def percentile(values, quantile):
    ordered = sorted(values)
    point = (len(ordered) - 1) * quantile
    low = math.floor(point)
    return ordered[low] + (ordered[math.ceil(point)] - ordered[low]) * (point-low)

def analyse(text, *, skip_seconds=0.0, duration=None, target_fps=60.0):
    if not math.isfinite(target_fps) or target_fps <= 0 or not math.isfinite(skip_seconds) or skip_seconds < 0:
        raise ValueError("Target FPS must be positive and skip seconds nonnegative.")
    if duration is not None and (not math.isfinite(duration) or duration <= 0):
        raise ValueError("Duration must be positive.")
    reader = csv.DictReader(io.StringIO(text))
    if not reader.fieldnames or "frame_time_us" not in reader.fieldnames:
        raise ValueError("CSV has no frame-time header; use a new complete problem report.")
    rows = []
    elapsed = 0.0
    malformed = 0
    for row in reader:
        try:
            if None in row or any(value is None for value in row.values()):
                malformed += 1
                continue
            frame = float(row["frame_time_us"])
            if not math.isfinite(frame) or frame <= 0:
                continue
            seconds = frame / 1e6
            start = elapsed
            elapsed += seconds
            if start < skip_seconds or duration is not None and start >= skip_seconds+duration:
                continue
            audio = any(float(row.get(key) or 0) > 0 for key in AUDIO)
            rows.append((frame/1000, audio))
        except (ValueError, TypeError):
            malformed += 1
    if not rows:
        raise ValueError("No complete frames in the requested window.")
    times = [r[0] for r in rows]
    threshold = 2 * 1000 / target_fps
    slow = [r for r in rows if r[0] > threshold]
    audio = [r for r in rows if r[1]]
    return {
        "frames": len(rows), "captured_seconds": sum(times)/1000,
        "average_fps": 1000/statistics.mean(times),
        "median_ms": statistics.median(times), "p95_ms": percentile(times,.95),
        "p99_ms": percentile(times,.99), "worst_ms": max(times),
        "slow_frame_threshold_ms": threshold, "slow_frames": len(slow),
        "frames_with_audio_stalls": len(audio),
        "slow_frames_with_audio_stalls": sum(r[1] for r in slow),
        "audio_stall_frame_p95_ms": percentile([r[0] for r in audio],.95) if audio else None,
        "audio_counters_available": any(k in reader.fieldnames for k in AUDIO),
        "incomplete_rows_ignored": malformed,
        "scope": "Selected captured frames; menus/loading are included unless excluded by the selected window.",
        "audio_note": "Same-frame overlap only. Warnings or overlap alone do not prove audio caused a hitch.",
    }

def analyse_zip(archive, **options):
    """Retain older captures while identifying their originating sessions."""
    entries=archive.infolist()
    if len(entries)>512 or sum(e.file_size for e in entries)>256*1024*1024:
        raise ValueError("Report exceeds the entry or expanded-size limit.")
    index={}
    try:
        entry=archive.getinfo("report-index.json")
        if entry.file_size<=1024*1024:
            parsed=json.loads(archive.read(entry))
            if isinstance(parsed,dict):index=parsed
    except (KeyError,ValueError,TypeError):
        pass
    records=index.get("files",[])
    metadata={r["entry"]:r for r in records if isinstance(r,dict) and isinstance(r.get("entry"),str)} if isinstance(records,list) else {}
    sessions = []
    for entry in archive.infolist():
        if not entry.filename.endswith(".jsonl") or entry.file_size > 2 * 1024 * 1024:
            continue
        for line in archive.read(entry).decode("utf-8-sig", errors="replace").splitlines():
            try:
                event = json.loads(line)
            except (ValueError, TypeError):
                continue
            if (isinstance(event, dict) and event.get("event") == "process.start"
                    and SESSION.fullmatch(str(event.get("session", "")))):
                sessions.append(event["session"])
    reported=index.get("latest_session")
    latest=reported if isinstance(reported,str) and SESSION.fullmatch(reported) else max(sessions) if sessions else None
    results = []
    for entry in archive.infolist():
        if not entry.filename.lower().endswith(".csv"):
            continue
        if entry.file_size > 64 * 1024 * 1024:
            raise ValueError("CSV exceeds the 64 MiB analysis limit.")
        result = analyse(archive.read(entry).decode("utf-8-sig", errors="replace"), **options)
        match = SESSION.search(Path(entry.filename).name)
        capture = match.group() if match else None
        item = {"file": entry.filename, "session": capture,
                "latest_report_session": latest,
                "matches_latest_session": capture == latest if capture and latest else None,
                **result}
        record=metadata.get(entry.filename,{})
        if record:
            item["source_commit"]=record.get("source_commit")
            item["tail_truncated"]=record.get("tail_truncated")
            app_commit=index.get("app_source_commit")
            item["matches_app_build"]=record.get("source_commit")==app_commit if app_commit and app_commit!="unknown" else None
        if capture and latest and capture != latest:
            item["session_warning"] = (
                "Historical CSV: these frames belong to an earlier game session, "
                "not the latest launch or its settings."
            )
        elif latest and not capture:
            item["session_warning"] = "The CSV filename cannot be associated with a game session."
        results.append(item)
    return results


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report",type=Path)
    parser.add_argument("--skip-seconds",type=float,default=0)
    parser.add_argument("--duration",type=float)
    parser.add_argument("--target-fps",type=float,default=60)
    args=parser.parse_args()
    if args.skip_seconds<0 or args.duration is not None and args.duration<=0 or not 1<=args.target_fps<=240:
        parser.error("Use a positive duration, nonnegative start and target FPS from 1 to 240.")
    results=[]
    if zipfile.is_zipfile(args.report):
        with zipfile.ZipFile(args.report) as z:
            results = analyse_zip(z, skip_seconds=args.skip_seconds,
                                  duration=args.duration, target_fps=args.target_fps)
    else:
        results=[{"file":args.report.name,**analyse(args.report.read_text(encoding='utf-8-sig'),skip_seconds=args.skip_seconds,duration=args.duration,target_fps=args.target_fps)}]
    if not results:
        raise ValueError("No frame CSV found. Reproduce the problem with Play with logs enabled.")
    print(json.dumps(results,indent=2))
if __name__=='__main__':
    main()
