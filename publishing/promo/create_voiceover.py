"""Generate the Arabic male voiceover for the promotional short."""

import asyncio
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / "_deps"))

import edge_tts  # noqa: E402


VOICE = "ar-SA-HamedNeural"
LINES = [
    "فاتورتك السعودية، بياناتها بين يديك.",
    "افتح قارئ فاتورة، وامسح رمز كيو آر بالكاميرا.",
    "أو استورد صورة الفاتورة، أو ملف بي دي إف.",
    "راجع اسم البائع، والتاريخ، والإجمالي، والضريبة بوضوح.",
    "احتفظ بقراءاتك، وارجع إليها متى احتجت.",
    "وتابع إحصاءات فواتيرك من مكان واحد.",
    "قارئ فاتورة. اقرأ، واحفظ، وتابع.",
]


async def main():
    out = ROOT / "work" / "voice"
    out.mkdir(parents=True, exist_ok=True)
    for index, line in enumerate(LINES, 1):
        destination = out / f"{index:02d}.mp3"
        await edge_tts.Communicate(line, VOICE, rate="-4%").save(str(destination))
        print(f"{destination.name}: {line}")


if __name__ == "__main__":
    asyncio.run(main())
