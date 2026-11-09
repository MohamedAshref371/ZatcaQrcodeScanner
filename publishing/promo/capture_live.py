"""Record a real Android emulator walkthrough using synthetic invoices only.

Run in an environment where adb can reach emulator-5582. The isolated
validationRelease app must be installed, and both virtual-camera images and the
demo-import QR from prepare_live_demo.py must already exist.
"""

import json
import subprocess
import time
from pathlib import Path


ROOT = Path(__file__).resolve().parent
WORK = ROOT / "work"
SERIAL = "emulator-5582"
MACRO = Path(r"C:\Users\Mohamed\AppData\Local\Android\Sdk\emulator\resources\macros\Walk_to_image_room")
PACKAGE = "com.poetscorner.invoiceqr.validation.release"
ACTIVITY = f"{PACKAGE}/com.poetscorner.invoiceqr.MainActivity"
BLANK = WORK / "blank-camera.png"
INVOICE = WORK / "store-camera-live.png"
REMOTE_VIDEO = "/sdcard/Download/qari-fatura-live-capture.mp4"


def adb(*args):
    subprocess.run(["adb", "-s", SERIAL, *map(str, args)], check=True,
                   stdout=subprocess.DEVNULL)


def main():
    WORK.mkdir(exist_ok=True)
    adb("emu", "virtualscene-image", "wall", BLANK.resolve())
    adb("emu", "automation", "play", MACRO)
    time.sleep(16)
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "pm", "clear", PACKAGE)
    adb("shell", "cmd", "locale", "set-app-locales", PACKAGE,
        "--user", "0", "--locales", "ar")
    adb("shell", "pm", "grant", PACKAGE, "android.permission.CAMERA")
    adb("shell", "pm", "clear", "com.google.android.documentsui")
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "settings", "put", "global", "sysui_demo_allowed", "1")
    adb("shell", "am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command", "enter")
    adb("shell", "am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command", "clock", "-e", "hhmm", "0941")
    adb("shell", "am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command", "battery", "-e", "level", "100", "-e", "plugged", "false")
    adb("shell", "am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command", "notifications", "-e", "visible", "false")
    adb("shell", "input", "keyevent", "3")
    time.sleep(1)

    video = subprocess.Popen(
        ["adb", "-s", SERIAL, "shell", "screenrecord", "--size", "1080x1920",
         "--bit-rate", "8000000", "--time-limit", "70", REMOTE_VIDEO],
        stdout=subprocess.DEVNULL, stderr=subprocess.PIPE,
    )
    start = time.monotonic()
    events = []

    def action(name, *command):
        timestamp = round(time.monotonic() - start, 3)
        adb(*command)
        events.append({"time": timestamp, "event": name})
        print(f"{timestamp:6.2f}s  {name}", flush=True)

    try:
        time.sleep(1.3)
        action("launch", "shell", "am", "start", "-n", ACTIVITY)
        time.sleep(3.2)
        action("camera QR appears", "emu", "virtualscene-image", "wall", INVOICE.resolve())
        time.sleep(4.4)
        action("clear camera wall", "emu", "virtualscene-image", "wall", BLANK.resolve())
        time.sleep(8.0)
        action("scanner tab", "shell", "input", "tap", "979", "1750")
        time.sleep(1.3)
        action("gallery import", "shell", "input", "tap", "108", "400")
        time.sleep(3.0)
        action("picker menu", "shell", "input", "tap", "60", "169")
        time.sleep(2.0)
        action("Downloads", "shell", "input", "tap", "222", "685")
        time.sleep(2.0)
        action("select demo invoice", "shell", "input", "tap", "284", "815")
        time.sleep(3.2)
        action("invoice details swipe", "shell", "input", "swipe", "540", "1410", "540", "690", "780")
        time.sleep(2.6)
        action("more details swipe", "shell", "input", "swipe", "540", "1390", "540", "720", "690")
        time.sleep(2.0)
        action("history tab", "shell", "input", "tap", "535", "1750")
        time.sleep(2.0)
        action("history scroll", "shell", "input", "swipe", "540", "1370", "540", "840", "780")
        time.sleep(1.8)
        action("statistics tab", "shell", "input", "tap", "324", "1750")
        time.sleep(2.5)
        action("statistics scroll", "shell", "input", "swipe", "540", "1430", "540", "800", "790")
        time.sleep(3.0)
        remaining = max(0.0, 70 - (time.monotonic() - start) + 0.5)
        time.sleep(remaining)
    finally:
        try:
            _, stderr = video.communicate(timeout=8)
        except subprocess.TimeoutExpired:
            video.kill()
            _, stderr = video.communicate()
        if video.returncode:
            raise RuntimeError(f"screenrecord failed: {stderr.decode(errors='replace')}")
    output = WORK / "live-capture.mp4"
    adb("pull", REMOTE_VIDEO, output)
    (WORK / "live-events.json").write_text(json.dumps(events, indent=2), encoding="utf-8")
    print(f"Saved {output}", flush=True)


if __name__ == "__main__":
    main()
