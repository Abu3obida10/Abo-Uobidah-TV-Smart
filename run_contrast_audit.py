#!/usr/bin/env python3
"""تدقيق تباين ألوان أسطح الإعدادات لكل سمات Ui.kt (WCAG، الحد 4.5 للنص العادي).
يعيد حساب Surfaces و Contrast.kt كما في الكود. يخرج برمز 1 إن فشل أي فحص."""
import re, sys, pathlib
root = pathlib.Path(__file__).parent / "app/src/main/java/com/uobidah/launcher"
src = (root / "Ui.kt").read_text(encoding="utf8")
blk = src[src.index("val all: List<UTheme>"):src.index("val normalIndices")]

def hexc(s):
    s = s.lstrip("#"); s = "FF" + s if len(s) == 6 else s
    return tuple(int(s[i:i+2], 16) for i in (0, 2, 4, 6))

def balanced(text, start):
    d = 0
    for i in range(start, len(text)):
        if text[i] == "(": d += 1
        elif text[i] == ")":
            d -= 1
            if d == 0: return text[start:i+1]

themes = []
for m in re.finditer(r"UTheme\(", blk):
    body = balanced(blk, m.end() - 1)
    name = re.search(r'"([^"]+)"', body).group(1)
    head = body.split("border =")[0].split("onAccent =")[0].split("club =")[0]
    cols = re.findall(r'c\("([^"]+)"\)', head)
    if len(cols) < 7: continue
    oa = re.search(r'onAccent = c\("([^"]+)"\)', body)
    themes.append(dict(name=name, bot=hexc(cols[1]), acc=hexc(cols[4]), text=hexc(cols[5]), dim=hexc(cols[6]),
                       onacc=hexc(oa.group(1)) if oa else (255, 255, 255, 255)))

def over(c, base):
    a = c[0] / 255
    return (255,) + tuple(int(c[i] * a + base[i] * (1 - a)) for i in (1, 2, 3))
def wa(c, a): return (a,) + c[1:]
def lin(v):
    v /= 255; return v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4
def lum(c): return .2126 * lin(c[1]) + .7152 * lin(c[2]) + .0722 * lin(c[3])
def ratio(a, b):
    x, y = lum(a), lum(b); hi, lo = max(x, y), min(x, y); return (hi + .05) / (lo + .05)
def mix(c, t, f): return (255,) + tuple(max(0, min(255, int(c[i] + (t - c[i]) * f))) for i in (1, 2, 3))
def ensure(fg, bg, mn=4.5):
    if ratio(fg, bg) >= mn: return fg
    t = 0 if lum(bg) > .5 else 255
    c = fg
    for i in range(1, 21):
        c = mix(fg, t, i * .05)
        if ratio(c, bg) >= mn: return c
    return c
def on_color(bg, pref, mn=4.5):
    if ratio(pref, bg) >= mn: return pref
    w, d = (255, 255, 255, 255), (255, 4, 18, 26)
    return w if ratio(w, bg) >= ratio(d, bg) else d
def fit_bg(bg, fg, mn=4.5):
    """يُدخل الخلفية نحو عكس لون النص حتى يتحقق التباين (مثل Contrast.fitBackground)."""
    if ratio(fg, bg) >= mn: return bg
    t = 0 if lum(fg) > .5 else 255
    c = bg
    for i in range(1, 21):
        c = mix(bg, t, i * .05)
        if ratio(fg, c) >= mn: return c
    return c
def is_light(t): return (t["text"][1] * 299 + t["text"][2] * 587 + t["text"][3] * 114) // 1000 < 110

PILL_ON, PILL_OFF, WHITE = (255, 4, 120, 87), (255, 71, 85, 105), (255, 255, 255, 255)
fails = 0
for t in themes:
    L, b = is_light(t), t["bot"]
    secA = over((245, 255, 255, 255), b) if L else over((220, 9, 22, 43), b)
    row = over((255, 236, 241, 248), b) if L else over((105, 17, 34, 59), b)
    panel = over((238, 255, 255, 255), b) if L else over((150, 0, 0, 0), b)
    idle = over(wa(t["acc"], 22), panel) if L else over((24, 255, 255, 255), panel)
    act = over(wa(t["acc"], 240), b)
    onacc = on_color(act, t["onacc"])
    act = fit_bg(act, onacc)
    checks = [("النص/الصف", ratio(t["text"], row)), ("الخافت/الصف", ratio(t["dim"], row)), ("الخافت/القسم", ratio(t["dim"], secA)),
              ("عنوان القسم", ratio(ensure(t["acc"], secA), secA)), ("أيقونة القائمة الجانبية", ratio(ensure(t["acc"], idle), idle)),
              ("نص فوق التمييز", ratio(onacc, act)), ("حبة مفعّل", ratio(WHITE, PILL_ON)), ("حبة متوقف", ratio(WHITE, PILL_OFF))]
    bad = [(n, round(v, 2)) for n, v in checks if v < 4.5]
    if bad: fails += 1; print("FAIL", "فاتح" if L else "داكن", t["name"], bad)
print(f"{len(themes)} سمة فُحصت، الفاشلة: {fails}")
sys.exit(1 if fails else 0)
