"""Rasterise the ASCII atlas TextPageRenderer draws with, from the bundled OFL font.

Usage: python genfont.py   (needs Pillow; writes assets/fonts/atlas_96.bin)

The output is gzipped but deliberately NOT named .gz — AGP's asset merger decompresses
and renames *.gz assets during packaging.

Blob format, before gzip:
  magic  "MRMF"            4 bytes
  version                   1  = 1
  cellW, cellH, baseline    3
  firstChar, glyphCount     2
  advances[glyphCount]      glyphCount bytes
  alpha[glyphCount][cellH][cellW]   1 byte per pixel
"""
import gzip
import struct
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

FONTS = Path(__file__).parent / "assets" / "fonts"
TTF = FONTS / "comic_neue_bold.ttf"
OUT = FONTS / "atlas_96.bin"

PX = 96
FIRST, LAST = 32, 126
COUNT = LAST - FIRST + 1

font = ImageFont.truetype(str(TTF), PX)
ascent, descent = font.getmetrics()
cellH = ascent + descent
baseline = ascent

# widest glyph decides the cell width
cellW = 0
advances = []
for c in range(FIRST, LAST + 1):
    ch = chr(c)
    adv = round(font.getlength(ch))
    advances.append(max(0, min(255, adv)))
    box = font.getbbox(ch)
    right = box[2] if box else 0
    cellW = max(cellW, adv, right)
cellW = min(255, cellW + 1)
assert cellH <= 255, cellH

blob = bytearray(b"MRMF")
blob += struct.pack(">BBBBBB", 1, cellW, cellH, baseline, FIRST, COUNT)
blob += bytes(advances)
for c in range(FIRST, LAST + 1):
    img = Image.new("L", (cellW, cellH), 0)
    ImageDraw.Draw(img).text((0, 0), chr(c), fill=255, font=font)
    blob += img.tobytes()

OUT.write_bytes(gzip.compress(bytes(blob), 9, mtime=0))
print(f"wrote {OUT.name}: {len(blob)} bytes raw, {OUT.stat().st_size} gzipped")
print(f"  cell={cellW}x{cellH} baseline={baseline} glyphs={COUNT}")
