"""أدوات مشتركة لتوليد وسائط README: لوحة الألوان والخط المضمَّن وعناصر الرسم."""
import base64, io, os
from fontTools import subset
from fontTools.ttLib import TTFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
FONT_DIR = os.path.join(ROOT, "app/src/main/res/font")
OUT = os.path.join(ROOT, "docs/media")

C = dict(bg="#0b1020", bg2="#121a30", surface="#18213a", line="#2c3858", text="#f3f5fa", mute="#a7b0c8",
         gold="#f2c84b", gold2="#e8a026", red="#f0524a", ok="#5fd6a0")

TEXT_RANGES = "U+0020-007E,U+00B7,U+0600-0652,U+066A-066C,U+06A9,U+06CC,U+200C-200F,U+2066-2069"

def font_css(weights=("regular", "bold", "extrabold")):
    css = ""
    for w, num in (("regular", 400), ("bold", 700), ("extrabold", 800)):
        if w not in weights: continue
        opts = subset.Options(); opts.layout_features = ["init","medi","fina","isol","rlig","liga","calt","ccmp","locl","kern","mark","mkmk","curs","rclt"]; opts.flavor = "woff2"; opts.hinting = False; opts.name_IDs = []; opts.notdef_outline = False; opts.glyph_names = False; opts.drop_tables += ["DSIG","GDEF_x","STAT","fvar","gasp"]
        f = TTFont(os.path.join(FONT_DIR, f"almarai_{w}.ttf"))
        s = subset.Subsetter(opts)
        s.populate(unicodes=subset.parse_unicodes(TEXT_RANGES)); s.subset(f)
        buf = io.BytesIO(); f.save(buf)
        css += "@font-face{font-family:'Almarai';font-weight:%d;src:url(data:font/woff2;base64,%s) format('woff2')}" % (num, base64.b64encode(buf.getvalue()).decode())
    return css

def svg(w, h, body, defs="", weights=("regular", "bold", "extrabold")):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" width="{w}" height="{h}">'
            f'<style>{font_css(weights)} text{{font-family:Almarai,sans-serif}}</style><defs>{defs}</defs>{body}</svg>')

def t(x, y, s, size=28, fill=None, weight=700, anchor="end", extra="", rtl=True):
    fill = fill or C["text"]
    if rtl: anchor = {"end": "start", "start": "end"}.get(anchor, anchor); extra += ' direction="rtl"'
    return f'<text x="{x}" y="{y}" font-size="{size}" font-weight="{weight}" fill="{fill}" text-anchor="{anchor}" {extra}>{s}</text>'

def rect(x, y, w, h, r=16, fill=None, stroke=None, sw=1.5, extra=""):
    a = f'fill="{fill or "none"}"'
    if stroke: a += f' stroke="{stroke}" stroke-width="{sw}"'
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" {a} {extra}/>'

BG_DEFS = f'''
<linearGradient id="bg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{C['bg']}"/><stop offset="1" stop-color="{C['bg2']}"/></linearGradient>
<linearGradient id="gold" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="{C['gold']}"/><stop offset="1" stop-color="{C['gold2']}"/></linearGradient>
<radialGradient id="glowG" cx=".5" cy=".5" r=".5"><stop offset="0" stop-color="{C['gold']}" stop-opacity=".35"/><stop offset="1" stop-color="{C['gold']}" stop-opacity="0"/></radialGradient>
<radialGradient id="glowR" cx=".5" cy=".5" r=".5"><stop offset="0" stop-color="{C['red']}" stop-opacity=".3"/><stop offset="1" stop-color="{C['red']}" stop-opacity="0"/></radialGradient>
<pattern id="dots" width="32" height="32" patternUnits="userSpaceOnUse"><circle cx="2" cy="2" r="1.4" fill="#ffffff" fill-opacity=".06"/></pattern>
'''

def backdrop(w, h):
    return (f'<rect width="{w}" height="{h}" fill="url(#bg)"/><rect width="{w}" height="{h}" fill="url(#dots)"/>'
            f'<circle cx="{w*0.18}" cy="{h*0.2}" r="{h*0.55}" fill="url(#glowR)"/><circle cx="{w*0.85}" cy="{h*0.85}" r="{h*0.6}" fill="url(#glowG)"/>')

def mark(x, y, size, color=None):
    """علامة التطبيق (مأخوذة من docs/brand/logo-b-mark.svg) بمقياس size."""
    k = size / 256; col = color or C["gold"]
    return (f'<g transform="translate({x},{y}) scale({k})"><path fill="none" stroke="{col}" stroke-width="22" stroke-linejoin="round" stroke-linecap="round" d="M52 214V82a30 30 0 0 1 30-30h92a30 30 0 0 1 30 30v132"/>'
            f'<path fill="none" stroke="{col}" stroke-width="20" stroke-linecap="round" d="M88 118h80M88 160h38"/><circle cx="166" cy="176" r="17" fill="{col}"/></g>')

def pill(xr, y, label, size=22, fill=None, stroke=None, color=None):
    w = int(len(label) * size * 0.52 + 44)
    x = xr - w
    out = rect(x, y, w, size + 24, (size + 24) / 2, fill=fill, stroke=stroke or C["line"])
    out += t(xr - w / 2, y + size + 6, label, size, color or C["text"], 700, "middle")
    return out, w

SITES = [("سبورت لايف", "رياضة", 0.72, "يعمل", C["red"], 6, 2, 41),
         ("تك أوبس", "تقنية", 0.4, "يعمل", C["red"], 4, 0, 28),
         ("دليل المطبخ", "طبخ", 1.0, "متوقف", C["gold"], 0, 3, 17)]

def phone(x, y, s=1.0, title="غرفة التحكم"):
    """هاتف يعرض بطاقات المواقع الحية، ببيانات تجريبية."""
    W, H = 300, 600
    o = f'<g transform="translate({x},{y}) scale({s})">'
    o += rect(0, 0, W, H, 40, fill="#0a0e1a", stroke="#3a4668", sw=3)
    o += rect(10, 10, W - 20, H - 20, 32, fill=C["bg"])
    o += rect(W / 2 - 40, 18, 80, 8, 4, fill="#1d2640")
    o += t(W - 26, 62, title, 24, C["text"], 800)
    o += t(W - 26, 86, "2 مواقع تعمل الآن", 13, C["gold"], 700)
    cy = 104
    for name, topic, prog, st, col, ready, stuck, pub in SITES:
        o += rect(18, cy, W - 36, 130, 18, fill=C["surface"], stroke=C["line"])
        o += rect(W - 62, cy + 14, 34, 34, 11, fill=col)
        o += t(W - 45, cy + 38, name[0], 17, "#1b1204", 800, "middle")
        o += t(W - 70, cy + 30, name, 15, C["text"], 800)
        o += t(W - 70, cy + 47, topic, 11, C["mute"], 400)
        o += rect(30, cy + 20, 52, 24, 12, fill="none", stroke=col)
        o += t(56, cy + 37, st, 11, col, 700, "middle")
        o += rect(30, cy + 62, W - 60, 6, 3, fill=C["line"])
        o += rect(W - 30 - (W - 60) * prog, cy + 62, (W - 60) * prog, 6, 3, fill=col)
        for i, (lab, v) in enumerate((("جاهز", ready), ("لم يُنشر", stuck), ("منشور", pub))):
            xx = W - 34 - i * 82
            o += t(xx, cy + 100, str(v), 20, C["gold"] if (v and i < 2) else C["text"], 800)
            o += t(xx, cy + 118, lab, 11, C["mute"], 400)
        cy += 142
    o += rect(18, H - 74, W - 36, 44, 22, fill="#101830", stroke=C["line"])
    for i, l in enumerate(("سجل", "مزودون", "مقالات", "مواقع", "توليد")):
        o += t(W - 50 - i * 54, H - 47, l, 11, C["gold"] if i == 4 else C["mute"], 700, "middle")
    return o + "</g>"

def save(name, content):
    p = os.path.join(OUT, name)
    open(p, "w", encoding="utf-8").write(content)
    print(name, len(content.encode()) // 1024, "KB")
