# Icônes de l'appli (à lancer depuis la racine du dépôt) : python3 tools/icons.py
# Icône minimaliste : page d'agenda blanche (en-tête bleu, 2 anneaux) + coche manuscrite,
# sur dégradé bleu. Génère l'icône adaptative (avant-plan PNG + fond dégradé XML) et l'icône classique.
import math
import os

from PIL import Image, ImageDraw, ImageFilter

TOP = (96, 160, 255, 255)      # haut du dégradé
BOTTOM = (30, 76, 204, 255)    # bas du dégradé
HEADER = (52, 112, 236, 255)   # en-tête de la page
INK = (30, 70, 185, 255)       # coche
SS = 4                          # suréchantillonnage


def lerp(a, b, t):
    return a + (b - a) * t


def gradient(size):
    g = Image.new("RGBA", (1, 256))
    for y in range(256):
        g.putpixel((0, y), tuple(int(round(lerp(TOP[i], BOTTOM[i], y / 255))) for i in range(4)))
    return g.resize((size, size), Image.BICUBIC)


def squircle_mask(size, n=4.2, scale=0.96):
    big = size * SS
    m = Image.new("L", (big, big), 0)
    r = big / 2 * scale
    pts = []
    for i in range(720):
        t = 2 * math.pi * i / 720
        c, s = math.cos(t), math.sin(t)
        pts.append((big / 2 + r * abs(c) ** (2 / n) * (1 if c >= 0 else -1),
                    big / 2 + r * abs(s) ** (2 / n) * (1 if s >= 0 else -1)))
    ImageDraw.Draw(m).polygon(pts, fill=255)
    return m.resize((size, size), Image.LANCZOS)


def bezier(p0, p1, p2, p3, n):
    out = []
    for i in range(n + 1):
        t = i / n
        u = 1 - t
        out.append((u ** 3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t ** 3 * p3[0],
                    u ** 3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t ** 3 * p3[1]))
    return out


def pen_stroke(d, pts, wmax, wmin, color):
    """Trait à extrémités rondes et effilées, comme un vrai coup de stylo."""
    acc = [0.0]
    for i in range(1, len(pts)):
        acc.append(acc[-1] + math.dist(pts[i - 1], pts[i]))
    total, step, j, s = acc[-1], wmax * 0.1, 0, 0.0
    while s <= total:
        while j < len(pts) - 2 and acc[j + 1] < s:
            j += 1
        t = (s - acc[j]) / ((acc[j + 1] - acc[j]) or 1)
        x, y = lerp(pts[j][0], pts[j + 1][0], t), lerp(pts[j][1], pts[j + 1][1], t)
        w = lerp(wmin, wmax, math.sin(math.pi * s / total) ** 0.45)
        d.ellipse([x - w / 2, y - w / 2, x + w / 2, y + w / 2], fill=color)
        s += step


def page_art(S, k):
    """La page seule (fond transparent). k = hauteur de la page / taille de l'image."""
    art = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ch = S * k
    cw = ch * 0.9
    x0, y0 = (S - cw) / 2, (S - ch) / 2 + S * 0.01
    rad = ch * 0.135
    # ombre douce
    sh = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle([x0, y0 + ch * 0.04, x0 + cw, y0 + ch * 1.04], radius=rad, fill=(8, 25, 80, 115))
    art.alpha_composite(sh.filter(ImageFilter.GaussianBlur(ch * 0.05)))
    d = ImageDraw.Draw(art)
    d.rounded_rectangle([x0, y0, x0 + cw, y0 + ch], radius=rad, fill=(255, 255, 255, 255))
    # en-tête bleu
    hb = ch * 0.24
    head = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(head).rounded_rectangle([x0, y0, x0 + cw, y0 + hb + rad], radius=rad, fill=HEADER)
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).rectangle([x0, y0, x0 + cw, y0 + hb], fill=255)
    art.paste(head, (0, 0), Image.composite(head.getchannel("A"), Image.new("L", (S, S), 0), mask))
    d = ImageDraw.Draw(art)
    for fx in (0.32, 0.68):
        cx, cy, r = x0 + cw * fx, y0 + hb * 0.48, ch * 0.037
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(255, 255, 255, 240))
    # coche manuscrite
    def P(a, b):
        return (x0 + cw * a, y0 + ch * b)
    pts = bezier(P(0.26, 0.60), P(0.31, 0.64), P(0.37, 0.70), P(0.42, 0.76), 120)
    pts += bezier(P(0.42, 0.76), P(0.52, 0.60), P(0.62, 0.48), P(0.76, 0.38), 160)[1:]
    pen_stroke(d, pts, ch * 0.07, ch * 0.033, INK)
    return art


def legacy(size):
    S = size * SS
    img = gradient(S)
    hl = Image.new("RGBA", (S, S), (255, 255, 255, 0))
    ImageDraw.Draw(hl).ellipse([-S * 0.3, -S * 0.8, S * 1.3, S * 0.40], fill=(255, 255, 255, 20))
    img.alpha_composite(hl.filter(ImageFilter.GaussianBlur(S * 0.05)))
    img.alpha_composite(page_art(S, 0.60))
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    out.paste(img, (0, 0), squircle_mask(S))
    return out.resize((size, size), Image.LANCZOS)


def foreground(size):
    # icône adaptative : 108 dp, zone toujours visible = cercle de 66 dp -> page de 48 dp de haut
    S = size * SS
    return page_art(S, 0.445).resize((size, size), Image.LANCZOS)


if __name__ == "__main__":
    dens = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
    for name, f in dens.items():
        folder = os.path.join("res", "mipmap-" + name)
        os.makedirs(folder, exist_ok=True)
        legacy(int(48 * f)).save(os.path.join(folder, "ic_launcher.png"))
        foreground(int(108 * f)).save(os.path.join(folder, "ic_launcher_foreground.png"))
    os.makedirs(os.path.join("res", "drawable"), exist_ok=True)
    with open(os.path.join("res", "drawable", "ic_launcher_background.xml"), "w") as fh:
        fh.write('<?xml version="1.0" encoding="utf-8"?>\n'
                 '<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">\n'
                 '    <gradient android:angle="270" android:startColor="#%02X%02X%02X" android:endColor="#%02X%02X%02X" />\n'
                 '</shape>\n' % (TOP[:3] + BOTTOM[:3]))
    legacy(512).save(os.path.join("docs", "icone.png"))
    print("ok")
