#!/usr/bin/env python3
"""Render the Play Store 512x512 app icon from the daily-widget picker preview
(app/src/main/res/drawable/preview_daily_widget.xml) so the store icon matches
exactly what users see when adding the widget.

Parses the vector's rounded-rect "pills" and thin divider rects, alpha-composites
the translucent fills over an opaque dark base, supersamples for crisp edges.

Usage: python3 make_play_icon.py [output.png]    (default: play_icon_512.png)
Requires: Pillow.
"""
import os, re, sys
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "app", "src", "main", "res", "drawable",
                   "preview_daily_widget.xml")
VIEW = 120.0
SS = 4
N = 512 * SS
scale = N / VIEW

def argb(hexstr):
    h = hexstr.lstrip('#')
    return (int(h[2:4],16), int(h[4:6],16), int(h[6:8],16), int(h[0:2],16))

base = Image.new("RGBA", (N, N), (11, 11, 13, 255))  # opaque dark base
xml = open(SRC).read()
for m in re.finditer(r'android:fillColor="(#[0-9A-Fa-f]{8})"\s+android:pathData="([^"]+)"', xml):
    fill = argb(m.group(1)); data = m.group(2)
    sx, sy = map(float, re.search(r'M([-\d.]+),([-\d.]+)', data).groups())
    hm = re.search(r'\sh([-\d.]+)', data); vm = re.search(r'\sv([-\d.]+)', data)
    W = float(hm.group(1)) if hm else 0.0
    H = float(vm.group(1)) if vm else 0.0
    am = re.search(r'\sa([-\d.]+),', data); R = float(am.group(1)) if am else 0.0
    layer = Image.new("RGBA", (N, N), (0,0,0,0))
    d = ImageDraw.Draw(layer)
    if R > 0:
        d.rounded_rectangle([(sx-R)*scale, sy*scale, (sx+W+R)*scale, (sy+H+2*R)*scale],
                            radius=R*scale, fill=fill)
    else:
        d.rectangle([sx*scale, sy*scale, (sx+W)*scale, (sy+H)*scale], fill=fill)
    base = Image.alpha_composite(base, layer)

out = sys.argv[1] if len(sys.argv) > 1 else "play_icon_512.png"
base.resize((512,512), Image.LANCZOS).convert("RGB").save(out)
print(f"wrote {out}")
