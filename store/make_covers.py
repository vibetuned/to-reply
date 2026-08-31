"""Typographic covers for the demo plays whose recordings carry no artwork.

Sized and weighted to stay legible at the 56dp tile the library renders them in
(~96px on the tablet, ~147px on the phone), so: short lines, heavy serif, high
contrast, no fine detail.
"""
import os

from PIL import Image, ImageDraw, ImageFont

S = 512
SERIF = "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf"
SANS = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

COVERS = [
    ("proposal", "THE\nPROPOSAL", "ANTON CHEKHOV", (0x6E, 0x1B, 0x2A), (0xF4, 0xEA, 0xDC)),
    ("trifles", "TRIFLES", "SUSAN GLASPELL", (0x15, 0x40, 0x3E), (0xF1, 0xEC, 0xDE)),
]


def spaced(text, px):
    return (" " * px).join(text)


for name, title, author, bg, fg in COVERS:
    img = Image.new("RGB", (S, S), bg)
    d = ImageDraw.Draw(img)

    # Hairline frame, a printed-playbill cue that survives downscaling.
    d.rectangle([26, 26, S - 27, S - 27], outline=(*fg, 255), width=3)

    lines = title.split("\n")
    size = 116 if len(lines) > 1 else 132
    font = ImageFont.truetype(SERIF, size)
    max_w = S - (150 if len(lines) > 1 else 190)
    while max(d.textlength(l, font=font) for l in lines) > max_w:
        size -= 4
        font = ImageFont.truetype(SERIF, size)

    lh = int(size * 1.12)
    block = lh * len(lines)
    y = (S - block) // 2 - 34
    for line in lines:
        d.text((S / 2, y), line, font=font, fill=fg, anchor="ma")
        y += lh

    rule_y = y + 26
    d.line([(S / 2 - 54, rule_y), (S / 2 + 54, rule_y)], fill=fg, width=3)

    af = ImageFont.truetype(SANS, 27)
    d.text((S / 2, rule_y + 30), spaced(author, 1), font=af, fill=fg, anchor="ma")

    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "covers", f"cover-{name}.jpg")
    img.save(out, "JPEG", quality=92)
    print(out)
