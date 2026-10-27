#!/usr/bin/env python3
"""Icon artwork for 化学实验室 Spatial — one design, every format.

The motif: a glass conical flask of glowing reagent with rising bubbles, and above it a water molecule whose
electron orbit is drawn as a full spectrum (the family mark shared with 物理实验室 Spatial), on a deep teal disc.

Outputs (run from the ChemistrySpatial/ directory):
  app/src/main/res/drawable/icon_3d_layer_0.png   PICO 3-D icon, back layer: the disc (space, lattice, orbit)
  app/src/main/res/drawable/icon_3d_layer_1.png   PICO 3-D icon, front layer: flask + molecule (pop out)
  app/src/main/res/drawable/icon_3d_sdf_{0,1}.png  relief maps: grey = alpha = 128 + 2.55 x signed distance
                                                  inside the shape (px) — the template's own encoding
  app/src/main/res/mipmap-anydpi/ic_spatial_launcher.png   flat round composite (2-D fallback)
  app/src/main/res/drawable/ic_launcher_foreground.png     adaptive-icon foreground, 1424 px full bleed
  docs/store/icon_1024.png                                 store icon, 1024 x 1024, opaque
"""
import colorsys
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from scipy.ndimage import distance_transform_edt

SS = 2
N = 1024 * SS
rng = np.random.default_rng(20261027)
Y, X = (np.mgrid[0:N, 0:N].astype(np.float32) + 0.5) / SS

# geometry (1024 units)
NECK = (466, 558, 300, 445)                 # x0, x1, y0, y1
BODY_BOTTOM, BODY_X0, BODY_X1 = 838, 262, 762
LIQ_TOP = 640
MOL_O = (512.0, 158.0); O_R, H_R, BOND = 50.0, 32.0, 94.0
ORBIT = dict(cx=512.0, cy=184.0, a=200.0, b=64.0, rot=math.radians(-14))


def lin(c):
    c = np.asarray(c, np.float32) / 255.0
    return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)


def srgb(l):
    l = np.clip(l, 0, 1)
    return np.where(l <= 0.0031308, l * 12.92, 1.055 * l ** (1 / 2.4) - 0.055)


def over(dst, dst_a, src, src_a):
    a = src_a[..., None]
    out_a = src_a + dst_a * (1 - src_a)
    out = (src * a + dst * dst_a[..., None] * (1 - a)) / np.maximum(out_a[..., None], 1e-6)
    return out, out_a


def mask(draw_fn):
    """Antialiased mask from PIL drawing at the working resolution (coordinates in 1024 units)."""
    im = Image.new("L", (N * 2, N * 2), 0)
    draw_fn(ImageDraw.Draw(im), 2 * SS)
    return np.asarray(im.resize((N, N), Image.LANCZOS), np.float32) / 255.0


def blur(a, r):
    im = Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8))
    return np.asarray(im.filter(ImageFilter.GaussianBlur(r * SS)), np.float32) / 255.0


def flask_outline(k):
    """Conical flask silhouette polygon (with rounded bottom corners), scaled by k."""
    x0, x1, y0, y1 = NECK
    pts = [(x0 - 10, y0 - 14), (x1 + 10, y0 - 14), (x1 + 10, y0), (x1, y0), (x1, y1)]
    r = 46
    # right slope down to the rounded bottom-right corner
    pts += [(BODY_X1 - r * 0.55, BODY_BOTTOM - r * 1.05)]
    for t in np.linspace(0, 1, 10):
        a = math.radians(-20 + 110 * t)
        pts.append((BODY_X1 - r + r * math.cos(a) * 0.95, BODY_BOTTOM - r + r * math.sin(a)))
    for t in np.linspace(0, 1, 10):
        a = math.radians(90 + 110 * t)
        pts.append((BODY_X0 + r + r * math.cos(a) * 0.95, BODY_BOTTOM - r + r * math.sin(a)))
    pts += [(BODY_X0 + r * 0.55, BODY_BOTTOM - r * 1.05), (x0, y1), (x0, y0), (x0 - 10, y0)]
    return [(px * k, py * k) for px, py in pts]


def inner_x_at(y):
    """Inner half-width of the flask body at height y (for the liquid)."""
    x0, x1, _, y1 = NECK
    t = np.clip((y - y1) / (BODY_BOTTOM - 40 - y1), 0, 1)
    left = x0 + (BODY_X0 + 26 - x0) * t
    right = x1 + (BODY_X1 - 26 - x1) * t
    return left + 10, right - 10


# ------------------------------------------------------------------ back layer
def background():
    r = np.hypot(X - 512, Y - 440) / 724.0
    c0, c1, c2 = lin([22, 92, 104]), lin([9, 34, 52]), lin([3, 8, 18])
    t = np.clip(r, 0, 1)[..., None]
    rgb = np.where(t < 0.45, c0 + (c1 - c0) * (t / 0.45), c1 + (c2 - c1) * ((t - 0.45) / 0.55))
    # faint hexagonal (benzene-ring) lattice, fading out from the centre
    hexes = Image.new("L", (N, N), 0); d = ImageDraw.Draw(hexes)
    s = 74 * SS; h = s * math.sqrt(3) / 2
    for row in range(-1, int(N / (1.5 * s)) + 2):
        for col in range(-1, int(N / (2 * h)) + 2):
            cx = col * 2 * h + (h if row % 2 else 0); cy = row * 1.5 * s
            pts = [(cx + s * math.cos(math.radians(60 * k + 30)), cy + s * math.sin(math.radians(60 * k + 30))) for k in range(7)]
            d.line(pts, fill=255, width=2 * SS)
    hx = np.asarray(hexes, np.float32) / 255.0 * np.exp(-(((X - 512) ** 2 + (Y - 520) ** 2) / (2 * 380.0 ** 2)))
    rgb = rgb + lin([80, 220, 210]) * (hx * 0.07)[..., None]
    for _ in range(45):
        sx, sy = rng.uniform(40, 984, 2)
        sr = rng.uniform(0.6, 1.5); br = rng.uniform(0.3, 0.9)
        g = np.exp(-((X - sx) ** 2 + (Y - sy) ** 2) / (2 * sr ** 2))
        rgb = rgb + lin([215, 240, 255]) * (g * br * 0.8)[..., None]
    # warm-teal glow behind the flask
    g = np.exp(-((X - 512) ** 2 + (Y - 690) ** 2) / (2 * 230.0 ** 2))
    rgb = rgb + lin([40, 200, 170]) * (g * 0.22)[..., None]
    return rgb


def spectrum_orbit():
    o = ORBIT
    c, s = math.cos(-o["rot"]), math.sin(-o["rot"])
    xr = (X - o["cx"]) * c - (Y - o["cy"]) * s
    yr = (X - o["cx"]) * s + (Y - o["cy"]) * c
    F = (xr / o["a"]) ** 2 + (yr / o["b"]) ** 2 - 1
    G = 2 * np.sqrt((xr / o["a"] ** 2) ** 2 + (yr / o["b"] ** 2) ** 2)
    dist = np.abs(F / np.maximum(G, 1e-6))
    a = np.clip((5.5 - dist) * SS + 0.5, 0, 1)
    th = np.arctan2(yr / o["b"], xr / o["a"])
    sfrac = (th % (2 * np.pi)) / (2 * np.pi)
    hue = 0.76 * (1 - np.abs(2 * sfrac - 1))
    lut = np.array([colorsys.hsv_to_rgb(h, 0.78, 1.0) for h in np.linspace(0, 0.76, 256)], np.float32)
    rgb = lin(lut[np.clip((hue / 0.76 * 255).astype(int), 0, 255)] * 255)
    front = yr > 0
    return rgb, a * np.where(front, 0, 0.6), a * np.where(front, 1, 0), blur(a, 8) * 0.8


# ------------------------------------------------------------------ front layer
def flask_masks():
    """Body mask (neck + cone, corners rounded by blur-and-threshold), its 7 px erosion, and the liquid."""
    x0, x1, y0, y1 = NECK
    raw = mask(lambda d, f: (d.polygon([(x0 * f, (y0 - 2) * f), (x1 * f, (y0 - 2) * f), (x1 * f, y1 * f),
                                        (BODY_X1 * f, BODY_BOTTOM * f), (BODY_X0 * f, BODY_BOTTOM * f), (x0 * f, y1 * f)], fill=255),
                                 d.rectangle([(x0 - 12) * f, (y0 - 16) * f, (x1 + 12) * f, y0 * f], fill=255)))
    body = np.clip((blur(raw, 16) - 0.5) * 6 + 0.5, 0, 1) * raw + (1 - raw) * 0       # rounded convex corners
    body = np.clip((blur(body, 1.0) - 0.5) * 4 + 0.5, 0, 1)
    inner = np.clip((blur(body, 7) - 0.93) * 30, 0, 1)                                # ~7 px erosion
    liquid = inner * np.clip((Y - LIQ_TOP) * SS + 0.5, 0, 1)
    return body, inner, liquid


def flask_and_liquid():
    body, inner, liq = flask_masks()
    t = np.clip((Y - LIQ_TOP) / (BODY_BOTTOM - LIQ_TOP), 0, 1)[..., None]
    liq_rgb = (lin([90, 245, 200]) + (lin([24, 120, 220]) - lin([90, 245, 200])) * t) * 1.15
    men = np.exp(-((Y - LIQ_TOP - 2) ** 2) / (2 * 2.2 ** 2)) * inner
    edge = np.clip(body - inner, 0, 1)
    glass_rgb = np.ones((N, N, 3), np.float32) * lin([200, 240, 255])
    hi = np.exp(-((X - (318 + (Y - 470) * -0.36)) ** 2) / (2 * 8.0 ** 2)) * ((Y > 480) & (Y < 790)) * inner
    neck_hi = np.exp(-((X - 482) ** 2) / (2 * 5.0 ** 2)) * ((Y > 300) & (Y < 440))
    rgb = np.zeros((N, N, 3), np.float32); a = np.zeros((N, N), np.float32)
    rgb, a = over(rgb, a, glass_rgb, inner * 0.13)
    rgb, a = over(rgb, a, liq_rgb, liq * 0.93)
    rgb, a = over(rgb, a, lin([230, 255, 245]) * np.ones((N, N, 3), np.float32), np.clip(men, 0, 1) * 0.9)
    for _ in range(26):
        by = rng.uniform(LIQ_TOP + 22, BODY_BOTTOM - 44)
        l0, r0 = inner_x_at(np.array(by)); bx = rng.uniform(float(l0) + 22, float(r0) - 22)
        rgb, a = bubble(rgb, a, bx, by, rng.uniform(5, 13))
    for (bx, by, br) in [(500, 520, 9), (526, 470, 7), (506, 410, 11), (521, 352, 8), (509, 300, 6)]:
        rgb, a = bubble(rgb, a, bx, by, br)
    rgb, a = over(rgb, a, glass_rgb, edge * 0.85)
    rgb, a = over(rgb, a, glass_rgb * 1.2, np.clip(hi * 0.55 + neck_hi * 0.5, 0, 1))
    return rgb, a, body


def shrink(pts, d):
    cx = sum(p[0] for p in pts) / len(pts); cy = sum(p[1] for p in pts) / len(pts)
    out = []
    for x, y in pts:
        vx, vy = x - cx, y - cy; l = math.hypot(vx, vy) or 1
        out.append((x - vx / l * d, y - vy / l * d))
    return out


def bubble(rgb, a, bx, by, br):
    d = np.hypot(X - bx, Y - by)
    ring = np.clip(1 - np.abs(d - br) / 1.6, 0, 1) * 0.85
    fill = np.clip((br - d) * SS + 0.5, 0, 1) * 0.18
    spec = np.exp(-((X - bx + br * 0.35) ** 2 + (Y - by + br * 0.35) ** 2) / (2 * (br * 0.22) ** 2)) * 0.9
    col = np.ones((N, N, 3), np.float32) * lin([235, 255, 250])
    return over(rgb, a, col, np.clip(ring + fill + spec, 0, 1))


def sphere(cx, cy, r, base, light=(-0.45, -0.55, 0.70)):
    d = np.hypot(X - cx, Y - cy)
    al = np.clip((r - d) * SS + 0.5, 0, 1)
    nx = (X - cx) / r; ny = (Y - cy) / r; nz = np.sqrt(np.clip(1 - nx ** 2 - ny ** 2, 0, 1))
    L = np.array(light); L = L / np.linalg.norm(L)
    diff = np.clip(nx * L[0] + ny * L[1] + nz * L[2], 0, 1)
    spec = diff ** 36 * 0.9
    col = lin(base) * (0.28 + 0.85 * diff)[..., None] + spec[..., None]
    return col, al


def stick(x0, y0, x1, y1, w):
    vx, vy = x1 - x0, y1 - y0; L = math.hypot(vx, vy)
    t = np.clip(((X - x0) * vx + (Y - y0) * vy) / (L * L), 0, 1)
    d = np.hypot(X - (x0 + t * vx), Y - (y0 + t * vy))
    al = np.clip((w - d) * SS + 0.5, 0, 1)
    shade = 0.55 + 0.45 * np.clip(1 - d / w, 0, 1)
    return lin([215, 222, 232]) * shade[..., None], al


def molecule():
    ox, oy = MOL_O
    hs = []
    for sgn in (-1, 1):
        ang = math.radians(90 + sgn * 104.5 / 2)                 # H–O–H 104.5°, pointing down-ish
        hs.append((ox + BOND * math.cos(ang), oy + BOND * math.sin(ang)))
    rgb = np.zeros((N, N, 3), np.float32); a = np.zeros((N, N), np.float32)
    for hx, hy in hs:
        c, al = stick(ox, oy, hx, hy, 10); rgb, a = over(rgb, a, c, al)
    for hx, hy in hs:
        c, al = sphere(hx, hy, H_R, [238, 242, 248]); rgb, a = over(rgb, a, c, al)
    c, al = sphere(ox, oy, O_R, [232, 62, 60]); rgb, a = over(rgb, a, c, al)
    return rgb, a, [(ox, oy, O_R)] + [(hx, hy, H_R) for hx, hy in hs]


def sdf_png(dist_inside):
    v = np.clip(128 + 2.55 * dist_inside, 0, 255).astype(np.uint8)
    return Image.fromarray(np.dstack([v, v, v, v]))


def to_img(rgb, a=None):
    s = (srgb(rgb) * 255 + 0.5).astype(np.uint8)
    im = Image.fromarray(s) if a is None else Image.fromarray(np.dstack([s, (np.clip(a, 0, 1) * 255 + 0.5).astype(np.uint8)]))
    return im.resize((1024, 1024), Image.LANCZOS)


def main():
    os.makedirs("docs/store", exist_ok=True)
    disc_a = np.clip((512 - np.hypot(X - 512, Y - 512)) * SS + 0.5, 0, 1)
    one = np.ones((N, N), np.float32)

    # back layer
    back = background()
    o_rgb, o_back, o_front, o_glow = spectrum_orbit()
    back = back + o_rgb * (o_glow * 0.5)[..., None]
    back, _ = over(back, one, o_rgb, o_back)
    # molecule glow
    g = np.exp(-((X - MOL_O[0]) ** 2 + (Y - MOL_O[1]) ** 2) / (2 * 110.0 ** 2))
    back = back + lin([120, 230, 255]) * (g * 0.12)[..., None]

    # front layer: flask, molecule, the front half of the orbit
    f_rgb, f_a, body = flask_and_liquid()
    m_rgb, m_a, atoms = molecule()
    front, front_a = over(f_rgb, f_a, m_rgb, m_a)
    front, front_a = over(front, front_a, o_rgb, o_front)

    to_img(back, disc_a).save("app/src/main/res/drawable/icon_3d_layer_0.png", optimize=True)
    to_img(front, front_a).save("app/src/main/res/drawable/icon_3d_layer_1.png", optimize=True)

    # relief maps (1024 px): disc, and the union of flask + atoms
    g1 = (np.mgrid[0:1024, 0:1024].astype(np.float32) + 0.5)
    d_disc = 512 - np.hypot(g1[1] - 512, g1[0] - 512)
    sil = np.asarray(Image.fromarray((body * 255).astype(np.uint8)).resize((1024, 1024), Image.LANCZOS)) > 127
    d_in = distance_transform_edt(sil); d_out = distance_transform_edt(~sil)
    d_flask = np.where(sil, d_in, -d_out)
    d_atoms = np.max([r - np.hypot(g1[1] - x, g1[0] - y) for x, y, r in atoms], axis=0)
    sdf_png(d_disc).save("app/src/main/res/drawable/icon_3d_sdf_0.png", optimize=True)
    sdf_png(np.maximum(d_flask, d_atoms)).save("app/src/main/res/drawable/icon_3d_sdf_1.png", optimize=True)

    flat, _ = over(back, one, front, front_a)
    to_img(flat, disc_a).save("app/src/main/res/mipmap-anydpi/ic_spatial_launcher.png", optimize=True)
    square = to_img(flat)
    square.save("docs/store/icon_1024.png", optimize=True)
    square.resize((1424, 1424), Image.LANCZOS).save("app/src/main/res/drawable/ic_launcher_foreground.png", optimize=True)
    print("icons written")


if __name__ == "__main__":
    main()
