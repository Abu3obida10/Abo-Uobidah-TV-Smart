# معاينة خلفية DuskSceneBg: منفذ بايثون لنفس خطوات الرسم في DuskSceneBg.kt (نفس java.util.Random ونفس الإحداثيات).
# ليست لقطة من التطبيق ولا محاكاة لواجهته؛ فقط تحقق بصري من شكل الخلفية لأن البناء غير متاح هنا.
from PIL import Image, ImageDraw, ImageChops
import math
class JRandom:
    def __init__(s,seed): s.s=(seed^0x5DEECE66D)&((1<<48)-1)
    def nxt(s,b):
        s.s=(s.s*0x5DEECE66D+0xB)&((1<<48)-1); r=s.s>>(48-b)
        return r-(1<<b) if r>=(1<<(b-1)) and b==32 else r
    def f(s): return s.nxt(24)/float(1<<24)
def hexc(h): h=h.lstrip('#'); return tuple(int(h[i:i+2],16) for i in (0,2,4))
def lerp(a,b,t): return tuple(a[i]+(b[i]-a[i])*t for i in range(3))
def grad(h,stops,pos,endfrac):
    col=[]
    for y in range(h):
        t=min(y/(h*endfrac),1.0)
        for i in range(len(pos)-1):
            if t<=pos[i+1]: u=(t-pos[i])/(pos[i+1]-pos[i]); col.append(lerp(stops[i],stops[i+1],max(0,u))); break
        else: col.append(stops[-1])
    return col
W,H=1280,720
im=Image.new('RGB',(W,H)); px=im.load()
sky=grad(H,[hexc(x) for x in('#241A52','#5B3A8C','#B4608F','#F0A08E')],[0,.38,.72,1],.78)
for y in range(H):
    for x in range(W): px[x,y]=tuple(int(v) for v in sky[y])
# glow
glow=Image.new('RGBA',(W,H),(0,0,0,0)); gp=glow.load(); cx,cy,r=W*.5,H*.74,W*.55
for y in range(H):
    for x in range(W):
        t=math.hypot(x-cx,y-cy)/r
        if t>=1: continue
        if t<.5: a=150+(50-150)*(t/.5); c=(255+(240-255)*(t/.5),190+(130-190)*(t/.5),150+(160-150)*(t/.5))
        else: u=(t-.5)/.5; a=50*(1-u); c=(240,130,160)
        gp[x,y]=(int(c[0]),int(c[1]),int(c[2]),int(a))
im=Image.alpha_composite(im.convert('RGBA'),glow); d=ImageDraw.Draw(im,'RGBA')
unit=W/1280; rnd=JRandom(20261126)
stars=[]; 
for i in range(60): stars.append((rnd.f(),rnd.f()*.3,.5+rnd.f()*.9,.2+rnd.f()*.4))
birds=[]
for i in range(9): birds.append((.30+rnd.f()*.22,.40+rnd.f()*.12,.7+rnd.f()*.6))
def ridge(top,low,step):
    pts=[(0,H),(0,H*(top+low)/2)]; x=0; up=True
    while x<W:
        x+=W*(step*.6+rnd.f()*step*.8)
        fr=top+rnd.f()*(low-top)*.4 if up else low-rnd.f()*(low-top)*.4
        pts.append((min(x,W),H*fr)); up=not up
    pts+= [(W,H)]; return pts
far=ridge(.58,.66,.10); mid=ridge(.68,.76,.12); near=ridge(.82,.88,.14)
for sx,sy,sr,sa in stars:
    d.ellipse([sx*W-sr*unit,sy*H-sr*unit,sx*W+sr*unit,sy*H+sr*unit],fill=(255,255,255,int(sa*255)))
for bx,by,bs in birds:
    s=7*unit*bs; x=bx*W; y=by*H
    d.line([(x-s,y-s*.4),(x-s*.4,y-s*.6),(x,y)],fill=hexc('#2A1A45'),width=2)
    d.line([(x,y),(x+s*.4,y-s*.6),(x+s,y-s*.4)],fill=hexc('#2A1A45'),width=2)
d.polygon(far,fill=hexc('#7C5A9E')); d.polygon(mid,fill=hexc('#4B3478'))
def pine(cx,base,h,col):
    c=hexc(col); tw=h*.035; d.rectangle([cx-tw,base-h*.12,cx+tw,base],fill=c); lh=h*.42; top0=base-h
    for k in range(3):
        hw=h*(.13+.07*k); t=top0+k*h*.22; d.polygon([(cx,t),(cx-hw,t+lh),(cx+hw,t+lh)],fill=c)
pine(W*.10,H*.84,H*.46,'#1E1240'); pine(W*.92,H*.80,H*.38,'#1E1240')
d.polygon(near,fill=hexc('#251748'))
pine(W*.03,H*.95,H*.30,'#150C30'); pine(W*.97,H*.95,H*.24,'#150C30')
im.convert('RGB').save('preview_background_dusk_R8_style_SIMULATION.png')
print('saved')
