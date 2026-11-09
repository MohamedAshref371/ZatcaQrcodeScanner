"""Record the live image-import flow from an already open blank scanner."""

import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parent
WORK = ROOT / "work"
SERIAL = "emulator-5582"
REMOTE = "/sdcard/Download/qari-fatura-import-capture.mp4"


def adb(*args):
    subprocess.run(["adb", "-s", SERIAL, *map(str, args)], check=True,
                   stdout=subprocess.DEVNULL)


def ui_nodes():
    subprocess.run(["adb", "-s", SERIAL, "shell", "uiautomator", "dump", "/sdcard/promo-ui.xml"],
                   check=True, stdout=subprocess.DEVNULL, timeout=15)
    data = subprocess.check_output(["adb", "-s", SERIAL, "exec-out", "cat", "/sdcard/promo-ui.xml"], timeout=10)
    return ET.fromstring(data).iter("node")


def wait_for(predicate, timeout=30):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            match = next((node for node in ui_nodes() if predicate(node)), None)
            if match is not None:
                return match
        except (subprocess.SubprocessError, ET.ParseError):
            pass
        time.sleep(0.8)
    raise TimeoutError("Expected Android picker UI element did not appear")


def center(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "")))
    return (x1 + x2) // 2, (y1 + y2) // 2


def main():
    adb("shell", "pm", "clear", "com.google.android.documentsui")
    video = subprocess.Popen(
        ["adb", "-s", SERIAL, "shell", "screenrecord", "--size", "1080x1920",
         "--bit-rate", "8000000", "--time-limit", "55", REMOTE],
        stdout=subprocess.DEVNULL, stderr=subprocess.PIPE,
    )
    start = time.monotonic()
    events = []

    def action(name, x, y):
        t = round(time.monotonic() - start, 3)
        adb("shell", "input", "tap", x, y)
        events.append({"time": t, "event": name})
        print(f"{t:6.2f}s  {name}", flush=True)

    time.sleep(1.0)
    action("open gallery import", 108, 400)
    menu = wait_for(lambda node: node.get("content-desc") == "Show roots")
    action("open picker menu", *center(menu))
    downloads = wait_for(lambda node: node.get("text") == "Downloads" and
                         int(re.findall(r"\d+", node.get("bounds", "[0,0][0,0]"))[1]) > 500)
    action("open Downloads", *center(downloads))
    invoice = wait_for(lambda node: node.get("text") == "demo-import-qr.png")
    action("select demo-import-qr.png", *center(invoice))
    wait_for(lambda node: "مقهى تجريبي" in node.get("text", ""))
    time.sleep(max(0.0, 55 - (time.monotonic() - start) + 0.5))
    _, stderr = video.communicate(timeout=8)
    if video.returncode:
        raise RuntimeError(stderr.decode(errors="replace"))
    output = WORK / "import-capture.mp4"
    adb("pull", REMOTE, output)
    (WORK / "import-events.json").write_text(json.dumps(events, indent=2), encoding="utf-8")
    print(f"Saved {output}", flush=True)


if __name__ == "__main__":
    main()
