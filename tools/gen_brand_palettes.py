#!/usr/bin/env python3
"""يولّد app/src/test/resources/brand_palettes_minimal_tv_icons.csv من مجلد أيقونات PNG.

الصيغة: الاسم,النمط,hex6:حصة;...;transparent:حصة   (hex من 6 خانات حروف صغيرة دائمًا).
الاستخدام: gen_brand_palettes.py <dir_with_soft_and_solid_pngs> [out.csv]
         gen_brand_palettes.py --check app/src/test/resources/brand_palettes_minimal_tv_icons.csv

خطأ النسخة السابقة: التكميم كان v//51 فيعطي 5 للقيمة 255 ثم المستوى 25+51*5=280=0x118
فكانت المفاتيح مثل 118118118. هنا يُقصّ المؤشر على 4 (أعلى مستوى 229=e5) ثم يُمدَّد أعلى مستوى إلى ff
حتى يبقى الأبيض أبيض والأحمر ff1919 كما في الأيقونة.
"""
import re, sys, collections
HEX6 = re.compile(r'^[0-9a-f]{6}$')

def level(v):
    q = min(4, v // 51)          # 0..4 (لا 5)
    return 0xff if q == 4 and v >= 243 else 25 + 51 * q   # 25,76,127,178,229 أو 255 للقريب من الأبيض

def key(r, g, b):
    k = '%02x%02x%02x' % (level(r), level(g), level(b))
    assert HEX6.match(k), k
    return k

def palette(path, top=6):
    from PIL import Image
    im = Image.open(path).convert('RGBA')
    total = im.width * im.height
    cnt = collections.Counter(); transparent = 0
    for r, g, b, a in im.getdata():
        if a < 128: transparent += 1
        else: cnt[key(r, g, b)] += 1
    items = ['%s:%.3f' % (k, c / total) for k, c in cnt.most_common(top)]
    if transparent: items.append('transparent:%.3f' % (transparent / total))
    return ';'.join(items)

def check(csv_path):
    n = bad = 0
    for i, l in enumerate(open(csv_path, encoding='utf-8'), 1):
        if not l.strip(): continue
        name, style, pal = l.rstrip('\n').split(',')
        for e in pal.split(';'):
            k, v = e.split(':'); float(v)
            if k != 'transparent' and not HEX6.match(k):
                bad += 1; print('سطر %d %s/%s: مفتاح غير صالح %r' % (i, name, style, k))
        n += 1
    print('%d سطر، مفاتيح غير صالحة: %d' % (n, bad)); return 1 if bad else 0

if __name__ == '__main__':
    if len(sys.argv) >= 3 and sys.argv[1] == '--check': sys.exit(check(sys.argv[2]))
    import os
    src = sys.argv[1]; out = sys.argv[2] if len(sys.argv) > 2 else 'brand_palettes_minimal_tv_icons.csv'
    rows = []
    for f in sorted(os.listdir(src)):
        if f.lower().endswith('.png'):
            style = 'solid' if 'solid' in f.lower() else 'soft'
            rows.append('%s,%s,%s' % (os.path.splitext(f)[0], style, palette(os.path.join(src, f))))
    open(out, 'w', encoding='utf-8', newline='\n').write('\n'.join(rows) + '\n')
    sys.exit(check(out))
