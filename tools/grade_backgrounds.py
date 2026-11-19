"""Colour-grade CC0 Screaming Brain Studios nebula tiles into one palette per Sky Legends sector.
Luminance is remapped through a 3-stop gradient (shadow -> mid -> highlight); tiles stay seamless
because grading is per-pixel. Output: WebP, opaque, ready for app assets."""
import sys, os
from PIL import Image, ImageOps, ImageEnhance
src_dir, out_dir = sys.argv[1], sys.argv[2]
os.makedirs(out_dir, exist_ok=True)
# (source tile, shadow, mid, highlight, contrast) per sector index
S = [
 ("Blue Nebula/Blue Nebula 6",     (4,5,16),   (40,48,120),  (150,170,255), 1.25),  # 0 orbital approach
 ("Blue Nebula/Blue Nebula 2",     (14,6,22),  (110,45,90),  (255,170,150), 1.15),  # 1 cloud city dusk
 ("Blue Nebula/Blue Nebula 5",     (6,8,14),   (55,65,85),   (185,200,220), 1.2),   # 2 monsoon storm
 ("Blue Nebula/Blue Nebula 3",     (2,10,14),  (20,80,90),   (130,230,220), 1.2),   # 3 iron coastline
 ("Purple Nebula/Purple Nebula 4", (10,3,2),   (120,30,10),  (255,150,50),  1.3),   # 4 volcanic ridge
 ("Green Nebula/Green Nebula 7",   (2,6,18),   (20,90,110),  (190,255,240), 1.15),  # 5 frozen abyss aurora
 ("Blue Nebula/Blue Nebula 1",     (3,8,22),   (30,70,150),  (140,200,255), 1.1),   # 6 stratosphere breach
 ("Purple Nebula/Purple Nebula 3", (14,2,6),   (120,15,35),  (255,110,120), 1.2),   # 7 the last sky
 ("Purple Nebula/Purple Nebula 5", (6,6,8),    (60,60,68),   (200,200,210), 1.3),   # 8 shattered moon
 ("Green Nebula/Green Nebula 5",   (10,4,22),  (70,40,110),  (150,255,150), 1.15),  # 9 nebula graveyard
 ("Green Nebula/Green Nebula 7",   (20,4,0),   (170,60,10),  (255,220,120), 1.2),   # 10 solar furnace
 ("Blue Nebula/Blue Nebula 6",     (14,9,2),   (120,85,25),  (255,225,150), 1.2),   # 11 ring world gold
 ("Purple Nebula/Purple Nebula 1", (1,1,3),    (60,25,10),   (255,150,60),  1.5),   # 12 event horizon
 ("Purple Nebula/Purple Nebula 8", (10,2,20),  (110,30,130), (90,240,255),  1.1),   # 13 quantum rift
 ("Purple Nebula/Purple Nebula 5", (4,10,2),   (60,110,20),  (210,255,90),  1.2),   # 14 hive core
 ("Purple Nebula/Purple Nebula 4", (16,2,4),   (150,25,35),  (255,210,90),  1.3),   # 15 galactic throne
]
def lerp(a, b, t): return tuple(int(a[i] + (b[i]-a[i])*t) for i in range(3))
for i, (name, sh, mid, hi, con) in enumerate(S):
    im = Image.open(f"{src_dir}/{name} - 1024x1024.png").convert("RGB")
    im = im.rotate(90 * (i % 4))  # 90-degree turns keep a seamless tile seamless
    lum = ImageOps.grayscale(im)
    # Stretch each tile to the full range first (sources are mostly near-black), then a gamma
    # >1 keeps the field dark with luminous wisps rather than a uniformly bright wash.
    lum = ImageOps.autocontrast(lum, cutoff=(1, 0.3))
    lum = ImageEnhance.Contrast(lum).enhance(con)
    lum = lum.point(lambda v: int(255 * (v / 255) ** 1.35))
    lut = []
    for v in range(256):
        t = v / 255
        c = lerp(sh, mid, t/0.5) if t < 0.5 else lerp(mid, hi, (t-0.5)/0.5)
        lut.append(c)
    out = Image.new("RGB", im.size)
    px = lum.load(); op = out.load()
    rl = [c[0] for c in lut]; gl = [c[1] for c in lut]; bl = [c[2] for c in lut]
    r = lum.point(rl); g = lum.point(gl); b = lum.point(bl)
    out = Image.merge("RGB", (r, g, b))
    # keep gameplay readable: cap brightness
    out = ImageEnhance.Brightness(out).enhance(0.85)
    out.save(f"{out_dir}/bg_sector_{i:02d}.webp", "WEBP", quality=80, method=6)
print("done")
