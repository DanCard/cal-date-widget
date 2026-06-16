#!/usr/bin/env python3
"""Compose captured widget crops into polished 1080x1920 Play Store screenshots."""
import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

W, H = 1080, 1920
OUTDIR = "fastlane/metadata/android/en-US/images/phoneScreenshots"
ICON = "fastlane/metadata/android/en-US/images/icon/1.png"

TOP = (0x62, 0x00, 0xEE)
BOT = (0x37, 0x00, 0xB3)

def font(size, bold=True):
    p = ("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold
         else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
    return ImageFont.truetype(p, size)

def gradient_bg():
    bg = Image.new("RGB", (W, H))
    px = bg.load()
    for y in range(H):
        t = y / (H - 1)
        c = tuple(int(TOP[i] + (BOT[i] - TOP[i]) * t) for i in range(3))
        for x in range(W):
            px[x, y] = c
    return bg

def rounded(img, radius):
    img = img.convert("RGBA")
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, img.size[0], img.size[1]], radius=radius, fill=255)
    img.putalpha(mask)
    return img

def center_text(draw, cx, y, text, fnt, fill):
    w = draw.textlength(text, font=fnt)
    draw.text((cx - w / 2, y), text, font=fnt, fill=fill)

def fit_font(draw, text, start, bold, max_w):
    s = start
    while s > 24 and draw.textlength(text, font=font(s, bold)) > max_w:
        s -= 2
    return font(s, bold)

def make(crop_path, headline, sub1, sub2, out_name):
    base = gradient_bg().convert("RGBA")
    draw = ImageDraw.Draw(base)

    # Small app icon as a rounded tile at the very top
    icon = rounded(Image.open(ICON).convert("RGBA").resize((120, 120)), 26)
    base.alpha_composite(icon, ((W - 120) // 2, 70))

    # Headline + subtext (headline auto-fits the width)
    center_text(draw, W / 2, 220, headline, fit_font(draw, headline, 60, True, W - 90), (255, 255, 255, 255))
    center_text(draw, W / 2, 312, sub1, font(34, False), (224, 215, 255, 255))
    if sub2:
        center_text(draw, W / 2, 358, sub2, font(34, False), (224, 215, 255, 255))

    # Widget crop as a dark rounded card with a soft shadow,
    # vertically centered in the region below the text block.
    region_top, region_bot = 440, 1870
    crop = Image.open(crop_path).convert("RGB")
    box_w, box_h = 1000, region_bot - region_top
    cw, ch = crop.size
    scale = min(box_w / cw, box_h / ch)
    crop = crop.resize((int(cw * scale), int(ch * scale)))
    pad = 36
    card = Image.new("RGB", (crop.size[0] + pad * 2, crop.size[1] + pad * 2), (0, 0, 0))
    card.paste(crop, (pad, pad))
    card = rounded(card, 48)

    cx = (W - card.size[0]) // 2
    cy = region_top + (box_h - card.size[1]) // 2
    # shadow
    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle(
        [cx + 8, cy + 14, cx + card.size[0] + 8, cy + card.size[1] + 14], radius=48, fill=(0, 0, 0, 110))
    shadow = shadow.filter(ImageFilter.GaussianBlur(18))
    base.alpha_composite(shadow)
    base.alpha_composite(card, (cx, cy))

    os.makedirs(OUTDIR, exist_ok=True)
    out = os.path.join(OUTDIR, out_name)
    base.convert("RGB").save(out, "PNG")
    print("wrote", out, Image.open(out).size)

make("/tmp/src_date2.png",   "Today's date, beautifully simple",
     "Custom formats, colors & shadows", "", "1_date.png")
make("/tmp/src_daily2.png",  "Today, front and center",
     "Upcoming events stay big — past ones fade", "", "2_daily.png")
make("/tmp/src_weekly_final.png", "Your whole week at a glance",
     "Today is highlighted; past days roll", "forward to show next week", "3_weekly.png")
