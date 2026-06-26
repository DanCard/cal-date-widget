#!/usr/bin/env python3
"""Regenerate the adaptive launcher icon foreground (and monochrome) from the
daily-widget picker preview, so the home-screen icon matches the Play Store icon.

Maps preview_daily_widget.xml's 120-unit canvas into the center safe zone of the
432-unit adaptive foreground viewport (new = 72 + old*2.4), dropping the preview's
outer card (the adaptive background layer supplies that dark backing).

Writes:
  app/src/main/res/drawable/ic_app_icon_foreground.xml       (colored)
  app/src/main/res/drawable/ic_app_icon_foreground_mono.xml  (single-tone)
"""
import os, re

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "app", "src", "main", "res", "drawable")
SRC = os.path.join(RES, "preview_daily_widget.xml")
OFF, SCALE = 72.0, 2.4   # 120-canvas -> center 288 of 432

def n(v): return f"{v:.2f}"

def rounded(x, y, w, h, r):
    return (f"M{n(x)},{n(y)} h{n(w)} a{n(r)},{n(r)} 0 0 1 {n(r)},{n(r)} "
            f"v{n(h)} a{n(r)},{n(r)} 0 0 1 -{n(r)},{n(r)} h-{n(w)} "
            f"a{n(r)},{n(r)} 0 0 1 -{n(r)},-{n(r)} v-{n(h)} "
            f"a{n(r)},{n(r)} 0 0 1 {n(r)},-{n(r)} z")

def rect(x, y, w, h):
    return f"M{n(x)},{n(y)} h{n(w)} v{n(h)} h-{n(w)} z"

shapes = []  # (fill, pathData)
xml = open(SRC).read()
for m in re.finditer(r'android:fillColor="(#[0-9A-Fa-f]{8})"\s+android:pathData="([^"]+)"', xml):
    fill, data = m.group(1), m.group(2)
    if fill.upper().startswith("#99"):   # drop the outer card -> background layer
        continue
    sx, sy = map(float, re.search(r'M([-\d.]+),([-\d.]+)', data).groups())
    hm = re.search(r'\sh([-\d.]+)', data); vm = re.search(r'\sv([-\d.]+)', data)
    am = re.search(r'\sa([-\d.]+),', data)
    W = float(hm.group(1)) if hm else 0.0
    H = float(vm.group(1)) if vm else 0.0
    R = float(am.group(1)) if am else 0.0
    x, y, w, h, r = OFF+sx*SCALE, OFF+sy*SCALE, W*SCALE, H*SCALE, R*SCALE
    pd = rounded(x, y, w, h, r) if R > 0 else rect(x, y, w, h)
    shapes.append((fill, pd))

def emit(fills_override=None):
    head = ('<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp"\n    android:height="108dp"\n'
            '    android:viewportWidth="432"\n    android:viewportHeight="432">\n')
    body = "".join(
        f'    <path android:fillColor="{fills_override or fill}"\n'
        f'        android:pathData="{pd}" />\n' for fill, pd in shapes)
    return head + body + "</vector>\n"

open(os.path.join(RES, "ic_app_icon_foreground.xml"), "w").write(emit())
open(os.path.join(RES, "ic_app_icon_foreground_mono.xml"), "w").write(emit("#FF000000"))
print(f"wrote foreground + mono ({len(shapes)} shapes)")
