# -*- coding: utf-8 -*-
"""ChaoxingHook 应用图标生成器 v4。

设计:沿用学习通启动图标语言(红渐变底 + 白色流星),但尾迹改为三道由细渐粗、
末端"消散"成星点的弧形扫痕,与官方图标区分开,同时保留一眼可辨的超星意象。
自适应图标几何基于 108dp 视口,内容收在中央 66dp 安全区内。
运行: python art/make_icon.py   → 预览到 art/preview/,资源写入 app/src/main/res/
"""
import math
import os
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "art", "preview")
RES = os.path.join(ROOT, "app", "src", "main", "res")
os.makedirs(OUT, exist_ok=True)

SS = 16                      # 超采样倍率(相对 108 视口)
MASTER = 108 * SS            # 1728
CX_CY = (54.0, 54.0)         # 内容缩放基准点

# ---------------- 可调参数 ----------------
P = dict(
    # 三道弧形尾迹:(轨道圆心 O 固定在 P['trail_o'],各道有半径/起止角/线宽渐变)
    # 角度为屏幕角(0=右,90=下),θ 从小到大 = 尾→头,流向右上
    trail_o=(90.0, 95.0),
    trails=[
        dict(r=66.0, a0=200.0, a1=225.0, w0=1.0, w1=6.4),
        dict(r=55.0, a0=205.0, a1=232.0, w0=1.0, w1=5.7),
        dict(r=44.0, a0=210.0, a1=238.0, w0=1.0, w1=5.0),
    ],
    # 流星头部五角星 + 尾迹消散星点
    star=(65.5, 33.5), star_ro=11.5, star_ri=4.9,
    dots=[((55.0, 40.5), 1.7), ((48.5, 44.5), 1.2)],
    # 光晕与配色(红底取自学习通图标 #F34B2B→#CE001B)
    glow=(52.0, 55.0), glow_r=28.0, glow_a=0.10, glow_col=(255, 255, 255),
    bg_top=(0xEF, 0x4A, 0x2B), bg_bot=(0xC4, 0x00, 0x1C),
    trail_top=(255, 255, 255), trail_bot=(0xFF, 0xE0, 0xD8),
    star_top=(255, 255, 255), star_bot=(0xFF, 0xE8, 0xE2),
)

_CACHE = {}


def cached(name, scale, fn):
    key = (name, scale)
    if key not in _CACHE:
        _CACHE[key] = fn()
    return _CACHE[key]


def lerp(a, b, t):
    return a + (b - a) * t


def mix(c1, c2, t):
    return tuple(int(round(lerp(c1[i], c2[i], t))) for i in range(3))


def T(scale):
    """围绕 (54,54) 的内容缩放变换(用于 legacy 图标放大内容)。"""
    def f(pt):
        return (CX_CY[0] + (pt[0] - CX_CY[0]) * scale,
                CX_CY[1] + (pt[1] - CX_CY[1]) * scale)
    return f


# ---------------- 几何 ----------------
def trail_polygon(tr, scale=1.0, steps=40):
    """单道尾迹的多边形顶点:由细渐宽的环形扇区 + 头部半圆帽。"""
    t = T(scale)
    O = t(P['trail_o'])
    r = tr['r'] * scale
    a0, a1 = tr['a0'], tr['a1']
    w0, w1 = tr['w0'] * scale, tr['w1'] * scale
    outer, inner = [], []
    for i in range(steps + 1):
        a = math.radians(a0 + (a1 - a0) * i / steps)
        k = i / steps
        w = lerp(w0, w1, k) / 2
        c, s = math.cos(a), math.sin(a)
        outer.append((O[0] + (r + w) * c, O[1] + (r + w) * s))
        inner.append((O[0] + (r - w) * c, O[1] + (r - w) * s))
    pts = outer
    # 头部半圆帽:沿运动方向(+t)从外缘扫到内缘
    H = (O[0] + r * math.cos(math.radians(a1)), O[1] + r * math.sin(math.radians(a1)))
    for i in range(1, 9):
        phi = math.radians(a1 + 180.0 * i / 9)
        pts.append((H[0] + w1 / 2 * math.cos(phi), H[1] + w1 / 2 * math.sin(phi)))
    pts.extend(reversed(inner))
    return pts


def all_trail_points(scale=1.0):
    pts = []
    for tr in P['trails']:
        pts.extend(trail_polygon(tr, scale))
    return pts


def star_points(g, scale=1.0):
    t = T(scale)
    c = t(g['star'])
    ro, ri = g['star_ro'] * scale, g['star_ri'] * scale
    pts = []
    for i in range(5):
        ao = math.radians(-90 + i * 72)
        ai = math.radians(-90 + 36 + i * 72)
        pts.append((c[0] + ro * math.cos(ao), c[1] + ro * math.sin(ao)))
        pts.append((c[0] + ri * math.cos(ai), c[1] + ri * math.sin(ai)))
    return pts


# ---------------- 蒙版 ----------------
def draw_trails_mask(scale=1.0):
    m = Image.new('L', (MASTER, MASTER), 0)
    d = ImageDraw.Draw(m)
    for tr in P['trails']:
        pts = [(x * SS, y * SS) for x, y in trail_polygon(tr, scale)]
        d.polygon(pts, fill=255)
    return m


def draw_star_mask(scale=1.0):
    m = Image.new('L', (MASTER, MASTER), 0)
    d = ImageDraw.Draw(m)
    pts = [(x * SS, y * SS) for x, y in star_points(P, scale)]
    d.polygon(pts, fill=255)
    return m


def draw_dots_mask(scale=1.0):
    m = Image.new('L', (MASTER, MASTER), 0)
    d = ImageDraw.Draw(m)
    t = T(scale)
    for (c, r) in P['dots']:
        c = t(c)
        r *= scale
        d.ellipse([(c[0] - r) * SS, (c[1] - r) * SS, (c[0] + r) * SS, (c[1] + r) * SS], fill=255)
    return m


def draw_glow_rgba(scale=1.0):
    c = T(scale)(P['glow'])
    r = P['glow_r'] * scale
    small = 432
    a = Image.new('L', (small, small), 0)
    px = a.load()
    sc = small / MASTER
    ccx, ccy, rr = c[0] * SS * sc, c[1] * SS * sc, r * SS * sc
    for y in range(small):
        for x in range(small):
            dist = math.hypot(x - ccx, y - ccy) / rr
            if dist < 1.0:
                px[x, y] = int((1.0 - dist) ** 2 * P['glow_a'] * 255)
    alpha = a.resize((MASTER, MASTER), Image.BILINEAR)
    out = Image.new('RGBA', (MASTER, MASTER), tuple(P['glow_col']) + (0,))
    out.putalpha(alpha)
    return out


def vgrad_mask(size, c_top, c_bot, mask, y0, y1):
    """按视口 y∈[y0,y1] 做垂直渐变为 mask 上色(与矢量渐变坐标一致)。"""
    strip = Image.new('RGB', (1, MASTER))
    for y in range(MASTER):
        vy = y / SS
        t = 0.0 if y1 <= y0 else min(1.0, max(0.0, (vy - y0) / (y1 - y0)))
        strip.putpixel((0, y), mix(c_top, c_bot, t))
    grad = strip.resize((MASTER, MASTER), Image.BILINEAR).convert('RGBA')
    grad.putalpha(mask)
    return grad


def y_gradient_image(size, top, bot):
    strip = Image.new('RGB', (1, 256))
    for y in range(256):
        strip.putpixel((0, y), mix(top, bot, y / 255.0))
    return strip.resize((size, size), Image.BILINEAR)


def build_foreground(size, scale=1.0, mono=False):
    fg = Image.new('RGBA', (MASTER, MASTER), (0, 0, 0, 0))
    if mono:
        white = Image.new('RGBA', (MASTER, MASTER), (255, 255, 255, 255))
        for name, fn in [('trails', draw_trails_mask), ('star', draw_star_mask), ('dots', draw_dots_mask)]:
            fg = Image.composite(white, fg, cached(name, scale, lambda f=fn: f(scale)))
    else:
        fg = Image.alpha_composite(fg, cached('glow', scale, lambda: draw_glow_rgba(scale)))
        tp = all_trail_points(scale)
        ty0 = min(p[1] for p in tp) - 4
        ty1 = max(p[1] for p in tp)
        sp = star_points(P, scale)
        sy0 = min(p[1] for p in sp); sy1 = max(p[1] for p in sp)
        white = Image.new('RGBA', (MASTER, MASTER), (255, 255, 255, 255))
        fg = Image.alpha_composite(fg, vgrad_mask(
            MASTER, P['trail_top'], P['trail_bot'],
            cached('trails', scale, lambda: draw_trails_mask(scale)), ty0, ty1))
        fg = Image.alpha_composite(fg, vgrad_mask(
            MASTER, P['star_top'], P['star_bot'],
            cached('star', scale, lambda: draw_star_mask(scale)), sy0, sy1))
        fg = Image.composite(white, fg, cached('dots', scale, lambda: draw_dots_mask(scale)))
    return fg.resize((size, size), Image.LANCZOS)


def build_background(size):
    return y_gradient_image(size, P['bg_top'], P['bg_bot']).convert('RGBA')


def build_icon(size, shape='round', scale=1.0, mono=False):
    bgim = build_background(size).copy()
    fg = build_foreground(size, scale=scale, mono=mono)
    out = Image.alpha_composite(bgim, fg)
    msk = Image.new('L', (size, size), 0)
    d = ImageDraw.Draw(msk)
    if shape == 'round':
        d.ellipse([0, 0, size - 1, size - 1], fill=255)
    elif shape == 'squircle':
        d.rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * 0.22), fill=255)
    elif shape == 'square':
        d.rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * 0.18), fill=255)
    else:
        return out
    put = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    put.paste(out, (0, 0), msk)
    return put


def checker(size, cell=16):
    im = Image.new('RGB', (size, size), (200, 200, 200))
    d = ImageDraw.Draw(im)
    for y in range(0, size, cell):
        for x in range(0, size, cell):
            if (x // cell + y // cell) % 2:
                d.rectangle([x, y, x + cell - 1, y + cell - 1], fill=(160, 160, 160))
    return im


def label(d, xy, text, size=22):
    try:
        f = ImageFont.load_default(size=size)
    except Exception:
        f = None
    d.text(xy, text, fill=(40, 40, 40), font=f)


def emit_resources():
    """把设计写入 Android 资源:矢量前景/单色/背景、自适应 XML、各密度 webp。"""
    def f(v):
        s = '%.2f' % v
        return s.rstrip('0').rstrip('.') if '.' in s else s

    def hexc(c):
        return ''.join('%02X' % v for v in c)

    def grad_linear(y0, y1, c0, c1, prop):
        return (f'<aapt:attr name="android:{prop}">'
                f'<gradient android:type="linear" android:startX="0" android:startY="{f(y0)}"'
                f' android:endX="0" android:endY="{f(y1)}">'
                f'<item android:offset="0" android:color="#FF{hexc(c0)}"/>'
                f'<item android:offset="1" android:color="#FF{hexc(c1)}"/>'
                f'</gradient></aapt:attr>')

    tp = all_trail_points(1.0)
    ty0 = min(p[1] for p in tp) - 4
    ty1 = max(p[1] for p in tp)
    sp = star_points(P, 1.0)
    sy0 = min(p[1] for p in sp); sy1 = max(p[1] for p in sp)
    trails_d = ' '.join(
        'M' + 'L'.join(f'{f(x)},{f(y)}' for x, y in tr) + 'Z'
        for tr in (trail_polygon(tr, 1.0) for tr in P['trails']))
    star_d = 'M' + 'L'.join(f'{f(x)},{f(y)}' for x, y in sp) + 'Z'
    dots_d = ' '.join('M{a},{b}a{r},{r} 0 1,0 {d},0a{r},{r} 0 1,0 -{d},0'.format(
        a=f(c[0] - r), b=f(c[1]), r=f(r), d=f(2 * r)) for c, r in P['dots'])
    g = P['glow']

    fg_xml = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- 由 art/make_icon.py 生成,几何与预览渲染同源 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M{f(g[0] - P['glow_r'])},{f(g[1])}a{f(P['glow_r'])},{f(P['glow_r'])} 0 1,0 {f(2 * P['glow_r'])},0a{f(P['glow_r'])},{f(P['glow_r'])} 0 1,0 -{f(2 * P['glow_r'])},0">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="radial"
                android:centerX="{f(g[0])}"
                android:centerY="{f(g[1])}"
                android:gradientRadius="{f(P['glow_r'])}">
                <item
                    android:offset="0"
                    android:color="#1AFFFFFF" />
                <item
                    android:offset="0.55"
                    android:color="#07FFFFFF" />
                <item
                    android:offset="1"
                    android:color="#00FFFFFF" />
            </gradient>
        </aapt:attr>
    </path>
    <path android:fillColor="#00000000" android:pathData="@placeholder@" />
</vector>
'''
    # 占位替换,避免 f-string 内嵌大段渐变导致转义混乱
    fg_xml = fg_xml.replace(
        '<path android:fillColor="#00000000" android:pathData="@placeholder@" />',
        f'''    <path
        android:pathData="{trails_d}">
        {grad_linear(ty0, ty1, P['trail_top'], P['trail_bot'], 'fillColor')}
    </path>
    <path
        android:pathData="{star_d}">
        {grad_linear(sy0, sy1, P['star_top'], P['star_bot'], 'fillColor')}
    </path>
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="{dots_d}" />
''')
    mono_xml = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- 单色(Android 13+ 主题图标):由 art/make_icon.py 生成 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="{trails_d}{star_d}{dots_d}" />
</vector>
'''
    bg_xml = f'''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <gradient
        android:angle="270"
        android:endColor="#{hexc(P['bg_bot'])}"
        android:startColor="#{hexc(P['bg_top'])}" />
</shape>
'''
    anydpi = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
'''
    with open(os.path.join(RES, 'drawable', 'ic_launcher_foreground.xml'), 'w', encoding='utf-8') as fp:
        fp.write(fg_xml)
    with open(os.path.join(RES, 'drawable', 'ic_launcher_monochrome.xml'), 'w', encoding='utf-8') as fp:
        fp.write(mono_xml)
    with open(os.path.join(RES, 'drawable', 'ic_launcher_background.xml'), 'w', encoding='utf-8') as fp:
        fp.write(bg_xml)
    for name in ('ic_launcher.xml', 'ic_launcher_round.xml'):
        with open(os.path.join(RES, 'mipmap-anydpi-v26', name), 'w', encoding='utf-8') as fp:
            fp.write(anydpi)
    # legacy webp
    densities = [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)]
    for dpi, size in densities:
        build_icon(size, 'square', scale=1.3).save(
            os.path.join(RES, f'mipmap-{dpi}', 'ic_launcher.webp'), 'WEBP', quality=90, method=6)
        build_icon(size, 'round', scale=1.3).save(
            os.path.join(RES, f'mipmap-{dpi}', 'ic_launcher_round.webp'), 'WEBP', quality=90, method=6)
    print('resources written to', RES)


def load_cx_icon(size):
    """加载学习通原图（预览对比用）；素材不入库，缺失时返回 None。"""
    p = os.path.join(OUT, '_cx_icon_hd.png')
    if not os.path.exists(p):
        return None
    return Image.open(p).convert('RGBA').resize((size, size), Image.LANCZOS)


def main():
    # ---- 资源输出 ----
    emit_resources()

    # ---- 单图导出 ----
    build_icon(1024, 'squircle', scale=1.0).save(os.path.join(OUT, 'adaptive_squircle_1024.png'))
    build_icon(1024, 'round', scale=1.0).save(os.path.join(OUT, 'adaptive_round_1024.png'))
    build_icon(1024, 'square', scale=1.3).save(os.path.join(OUT, 'legacy_flat_1024.png'))
    build_icon(1024, 'round', scale=1.3).save(os.path.join(OUT, 'legacy_round_1024.png'))
    fg = build_foreground(1024)
    chk = checker(1024, 32).convert('RGBA')
    chk.alpha_composite(fg)
    chk.convert('RGB').save(os.path.join(OUT, 'fg_on_checker_1024.png'))
    mon = build_foreground(1024, mono=True)
    for i, tint in enumerate([(0x67, 0x50, 0xA4), (0xE8, 0xEA, 0xED), (0x10, 0x1C, 0x2E)]):
        im = Image.new('RGBA', (1024, 1024), tint + (255,))
        im.alpha_composite(mon)
        im.convert('RGB').save(os.path.join(OUT, f'mono_{i}.png'))

    # ---- 预览拼图（含学习通原图对比；对比素材缺失时自动跳过该栏） ----
    cx300 = load_cx_icon(300)
    sheet_w = 1500 if cx300 else 1150
    sheet = Image.new('RGB', (sheet_w, 1090), (0xEC, 0xEF, 0xF4))
    d = ImageDraw.Draw(sheet)
    label(d, (30, 16), 'adaptive                 legacy (1.3)            monochrome', 26)
    x = 30
    for shape, sc in [('squircle', 1.0), ('round', 1.0), ('square', 1.3), ('round', 1.3)]:
        im = build_icon(300, shape, scale=sc)
        sheet.paste(im, (x, 70), im)
        x += 320
    if cx300 is not None:
        label(d, (30, 16), 'adaptive                 legacy (1.3)            monochrome            学习通原图对比', 26)
        sheet.paste(cx300, (x, 70), cx300)
        label(d, (1010, 420), '学习通 96px:', 26)
        cx2 = load_cx_icon(96)
        if cx2 is not None:
            sheet.paste(cx2, (1030, 460), cx2)
    x = 30
    for i in range(3):
        im = Image.open(os.path.join(OUT, f'mono_{i}.png')).resize((300, 300), Image.LANCZOS)
        sheet.paste(im.convert('RGB'), (x, 420))
        x += 320
    label(d, (30, 760), 'small sizes (legacy flat):', 26)
    x = 30
    for s in (192, 144, 96, 72, 48):
        im = build_icon(s, 'square', scale=1.3)
        sheet.paste(im, (x, 800), im)
        x += 230
    label(d, (1230, 760), 'adaptive 48-96:', 26)
    x = 1230
    for s in (48, 72, 96):
        im = build_icon(s, 'round', scale=1.0)
        sheet.paste(im, (x, 800 + (96 - s)), im)
        x += 105
    sheet.save(os.path.join(OUT, 'mockup.png'))
    print('saved to', OUT)


if __name__ == '__main__':
    main()
