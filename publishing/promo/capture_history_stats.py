"""Record live history and statistics navigation after importing demo invoices."""

import json
import subprocess
import time
from pathlib import Path


ROOT = Path(__file__).resolve().parent
WORK = ROOT / "work"
SERIAL = "emulator-5582"
REMOTE = "/sdcard/Download/qari-fatura-history-stats.mp4"


def adb(*args):
    subprocess.run(["adb", "-s", SERIAL, *map(str, args)], check=True,
                   stdout=subprocess.DEVNULL)


def main():
    video = subprocess.Popen(
        ["adb", "-s", SERIAL, "shell", "screenrecord", "--size", "1080x1920",
         "--bit-rate", "8000000", "--time-limit", "30", REMOTE],
        stdout=subprocess.DEVNULL, stderr=subprocess.PIPE,
    )
    start = time.monotonic()
    events = []

    def action(name, *command):
        t = round(time.monotonic() - start, 3)
        adb("shell", "input", *command)
        events.append({"time": t, "event": name})
        print(f"{t:6.2f}s  {name}", flush=True)

    time.sleep(1)
    action("history tab", "tap", "535", "1750")
    time.sleep(5)
    action("history scroll", "swipe", "540", "1430", "540", "860", "800")
    time.sleep(2)
    action("statistics tab", "tap", "324", "1750")
    time.sleep(6)
    action("statistics scroll", "swipe", "540", "1420", "540", "770", "850")
    time.sleep(max(0.0, 30 - (time.monotonic() - start) + 0.5))
    _, stderr = video.communicate(timeout=8)
    if video.returncode:
        raise RuntimeError(stderr.decode(errors="replace"))
    output = WORK / "history-stats-capture.mp4"
    adb("pull", REMOTE, output)
    (WORK / "history-stats-events.json").write_text(json.dumps(events, indent=2), encoding="utf-8")
    print(f"Saved {output}", flush=True)


if __name__ == "__main__":
    main()
