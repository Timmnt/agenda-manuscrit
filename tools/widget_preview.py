# Aperçu du widget (liste des widgets du lanceur) : la page de demain, stylisée.
# Usage : python3 tools/widget_preview.py res/drawable-nodpi/widget_preview.png
import math, sys
from PIL import Image, ImageDraw, ImageFont
W, H, S = 360, 440, 3
img = Image.new("RGBA", (W*S, H*S), (0,0,0,0))
d = ImageDraw.Draw(img)
def R(*a): return [v*S for v in a]
d.rounded_rectangle(R(0,0,W,H), radius=26*S, fill=(255,255,255,255))
def font(size, bold=False):
    for f in (["/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"] if bold else ["/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"]):
        try: return ImageFont.truetype(f, size*S)
        except Exception: pass
    return ImageFont.load_default()
d.text((22*S, 18*S), "Vendredi", font=font(34, True), fill=(35,37,44,255))
d.text((23*S, 62*S), "6 novembre", font=font(16), fill=(123,127,139,255))
d.rounded_rectangle(R(268,24,340,48), radius=12*S, fill=(232,239,252,255), outline=(43,95,208,255), width=int(1.4*S))
d.text((280*S, 28*S), "Demain", font=font(12, True), fill=(43,95,208,255))
d.line(R(14,92,346,92), fill=(42,44,51,85), width=int(1.5*S))
for i,(name,col) in enumerate([("8h–10h Maths",(43,95,208)),("10h15 Élec",(224,123,0))]):
    x = 18 + i*128
    d.rounded_rectangle(R(x,100,x+118,118), radius=9*S, fill=col+(40,))
    d.ellipse(R(x+6,105,x+14,113), fill=col+(255,))
    d.text(((x+19)*S, 102*S), name, font=font(11), fill=(58,61,70,255))
for y in range(132, H-10, 26):
    d.line(R(10,y,350,y), fill=(214,225,239,255), width=S)
d.line(R(38,94,38,H), fill=(240,189,189,255), width=S)
# cases + écriture
def scribble(x0, y, x1):
    pts=[]; x=x0
    while x<=x1:
        t=(x-x0)*0.55; pts.append(((x)*S, (y-abs(math.sin(t))*7+2)*S)); x+=0.6
    d.line(pts, fill=(28,29,34,255), width=int(2.2*S), joint="curve")
rows=[(150, True, 230),(176, False, 300),(202, False, 200),(254, True, 260)]
for y,chk,x1 in rows:
    d.rounded_rectangle(R(48,y-14,64,y+2), radius=4*S, outline=(28,29,34,255), width=int(2*S), fill=(28,29,34,30) if chk else None)
    if chk: d.line([(51*S,(y-6)*S),(55*S,(y-2)*S),(62*S,(y-12)*S)], fill=(28,29,34,255), width=int(2.6*S), joint="curve")
    scribble(74, y-2, x1)
d.rounded_rectangle(R(240,220,320,244), radius=7*S, outline=(214,47,60,255), width=int(2*S), fill=(214,47,60,22))
d.text((268*S, 224*S), "DS", font=font(14, True), fill=(214,47,60,255))
img = img.resize((W, H), Image.LANCZOS)
img.save(sys.argv[1])
print("ok")
