#!/usr/bin/env python3
"""Build the 1024x500 Play Store feature graphic."""
import os
from PIL import Image, ImageDraw, ImageFont

W, H = 1024, 500
OUT = "fastlane/metadata/android/en-US/images/featureGraphic/1.png"

# --- Dark gradient background, matching the icon's own near-black tile ---
top = (0x1c, 0x1c, 0x1e)
bot = (0x0a, 0x0a, 0x0b)
bg = Image.new("RGB", (W, H))
px = bg.load()
for y in range(H):
    t = y / (H - 1)
    px[0, y] = tuple(int(top[i] + (bot[i] - top[i]) * t) for i in range(3))
for x in range(1, W):  # copy first column across (gradient is vertical only)
    for y in range(H):
        px[x, y] = px[0, y]

# --- Icon rendered as a rounded app tile with a soft shadow ---
TILE = 300
RADIUS = 66
icon = Image.open("fastlane/metadata/android/en-US/images/icon/1.png").convert("RGBA").resize((TILE, TILE))
mask = Image.new("L", (TILE, TILE), 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, TILE, TILE], radius=RADIUS, fill=255)
icon.putalpha(mask)

icon_x, icon_y = 80, (H - TILE) // 2
# soft drop shadow
shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
sdraw = ImageDraw.Draw(shadow)
sdraw.rounded_rectangle([icon_x + 6, icon_y + 10, icon_x + TILE + 6, icon_y + TILE + 10],
                        radius=RADIUS, fill=(0, 0, 0, 140))
try:
    from PIL import ImageFilter
    shadow = shadow.filter(ImageFilter.GaussianBlur(12))
except Exception:
    pass
base = bg.convert("RGBA")
base.alpha_composite(shadow)
# subtle border so the black icon tile doesn't melt into the dark background
border = Image.new("RGBA", (W, H), (0, 0, 0, 0))
ImageDraw.Draw(border).rounded_rectangle(
    [icon_x - 1, icon_y - 1, icon_x + TILE + 1, icon_y + TILE + 1],
    radius=RADIUS + 1, outline=(255, 255, 255, 40), width=2
)
base.alpha_composite(border)
base.alpha_composite(icon, (icon_x, icon_y))

# --- Text ---
def font(size, bold=True):
    for p in (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold
        else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ):
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()

draw = ImageDraw.Draw(base)
tx = icon_x + TILE + 60
avail = W - tx - 48  # right margin

# Largest title size that fits the available width
title = "Cal Date Widget"
tsize = 72
while tsize > 30:
    f = font(tsize, True)
    if draw.textlength(title, font=f) <= avail:
        break
    tsize -= 2
title_font = font(tsize, True)
sub_font = font(30, False)

# Vertically center the title + 2 tagline lines as a block
draw.text((tx, 188), title, font=title_font, fill=(255, 255, 255, 255))
draw.text((tx, 188 + tsize + 18), "Minimal date & calendar widgets", font=sub_font, fill=(190, 190, 200, 255))
draw.text((tx, 188 + tsize + 18 + 40), "for your home screen", font=sub_font, fill=(190, 190, 200, 255))

base.convert("RGB").save(OUT, "PNG")
print("feature graphic written:", Image.open(OUT).size, Image.open(OUT).mode)
