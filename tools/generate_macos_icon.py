#!/usr/bin/env python3
"""Generuje composeApp/icons/toaf_icon_macos.icns z kwadratowego pliku PNG.

macOS nie czyta .ico ani .png jako ikony pakietu - jpackage wymaga .icns.
Format .icns przyjmuje osadzone PNG (macOS 10.7+), wiec plik da sie zlozyc
na dowolnym systemie, bez narzedzia iconutil dostepnego tylko na Macu.

Zrodlo musi byc KWADRATOWE. Domyslnie uzywany jest toaf_icon_linux.png, bo
toaf_icon_windows.ico ma 63x64 piksele (nie jest kwadratem). Im wiekszy
oryginal, tym ostrzejsza ikona - docelowo warto podac master 1024x1024.

Uzycie:
    python tools/generate_macos_icon.py [zrodlo.png] [wynik.icns]
"""

import io
import struct
import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    sys.exit("Brak biblioteki Pillow. Zainstaluj: pip install Pillow")

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_SOURCE = ROOT / "composeApp/src/commonMain/composeResources/drawable/toaf_icon_linux.png"
DEFAULT_TARGET = ROOT / "composeApp/icons/toaf_icon_macos.icns"

# Typy ICNS z osadzonym PNG: (czteroznakowy typ, rozmiar w pikselach)
TYPES = [
    (b"icp4", 16),    # 16x16
    (b"icp5", 32),    # 32x32
    (b"ic11", 32),    # 16x16@2x
    (b"ic12", 64),    # 32x32@2x
    (b"ic07", 128),   # 128x128
    (b"ic13", 256),   # 128x128@2x
    (b"ic08", 256),   # 256x256
    (b"ic14", 512),   # 256x256@2x
    (b"ic09", 512),   # 512x512
    (b"ic10", 1024),  # 512x512@2x
]


def build_icns(source: Path, target: Path) -> None:
    image = Image.open(source).convert("RGBA")
    if image.width != image.height:
        sys.exit(f"Zrodlo musi byc kwadratowe, a ma {image.width}x{image.height}: {source}")

    # Reprezentacje ponad 4x wieksze od zrodla to sama interpolacja - macOS zrobi
    # dokladnie to samo, powiekszajac najwieksza dostepna, wiec tylko puchlby plik.
    # Master 512x512 lub wiekszy daje komplet rozmiarow az do 1024.
    limit = image.width * 4
    skipped = [size for _, size in TYPES if size > limit]
    if skipped:
        print(f"UWAGA: zrodlo ma tylko {image.width}x{image.width} px - pomijam rozmiary "
              f"{sorted(set(skipped))}. Podaj wiekszy master, zeby ikona byla ostra.")

    chunks = []
    for kind, size in TYPES:
        if size > limit:
            continue
        resized = image.resize((size, size), Image.LANCZOS)
        buffer = io.BytesIO()
        resized.save(buffer, format="PNG", optimize=True)
        data = buffer.getvalue()
        # Naglowek fragmentu: typ (4 B) + dlugosc razem z naglowkiem (4 B, big-endian)
        chunks.append(kind + struct.pack(">I", len(data) + 8) + data)

    body = b"".join(chunks)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(b"icns" + struct.pack(">I", len(body) + 8) + body)
    print(f"Zapisano {target} ({target.stat().st_size} B, {len(chunks)} reprezentacji) "
          f"ze zrodla {source} ({image.width}x{image.width})")


if __name__ == "__main__":
    src = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_SOURCE
    dst = Path(sys.argv[2]) if len(sys.argv) > 2 else DEFAULT_TARGET
    build_icns(src, dst)
