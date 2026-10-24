"""Render all three privacy-safe posters from one shared, fixed-size layout system."""
import argparse
from functools import lru_cache
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app" / "build" / "outputs" / "promo-drafts"
POSTER_SIZE = (1600, 1000)
PHONE_WIDTH, PHONE_HEIGHT, FRAME_PAD, PHONE_TOP = 330, 715, 12, 180
PAPER, CARD, INK, GREEN, MUTED, LINE = "#F6F3EB", "#FFFDF8", "#292F2B", "#344E43", "#777B71", "#E4E3D8"
FONT = Path("C:/Windows/Fonts/msyh.ttc")
BOLD = Path("C:/Windows/Fonts/msyhbd.ttc")


def font(size, bold=False):
    return ImageFont.truetype(str(BOLD if bold else FONT), size)


def text(draw, xy, value, size=16, color=INK, bold=False, anchor=None):
    draw.text(xy, value, font=font(size, bold), fill=color, anchor=anchor)


def box(draw, bounds, fill=CARD, radius=18, outline=None, width=1):
    draw.rounded_rectangle(bounds, radius=radius, fill=fill, outline=outline, width=width)


def icon(draw, name, x, y, size=20, color=GREEN):
    s = size / 24
    def pts(values):
        return [(x + a * s, y + b * s) for a, b in values]
    def line(values):
        draw.line(pts(values), fill=color, width=max(1, round(1.7 * s)), joint="curve")
    if name == "home":
        line([(3, 10), (12, 3), (21, 10), (21, 21), (3, 21), (3, 10)])
        line([(9, 21), (9, 14), (15, 14), (15, 21)])
    elif name == "card":
        draw.rounded_rectangle((x+2*s,y+4*s,x+22*s,y+20*s), radius=3*s, outline=color, width=max(1,round(1.7*s)))
        line([(3, 9), (21, 9)]);line([(6, 15), (11, 15)])
    elif name == "bills":
        line([(5, 2), (19, 2), (19, 22), (16, 20), (12, 22), (8, 20), (5, 22), (5, 2)])
        line([(9, 8), (15, 8)]);line([(9, 13), (15, 13)])
    elif name == "person":
        draw.ellipse((x+8*s,y+2*s,x+16*s,y+10*s), outline=color, width=max(1,round(1.7*s)))
        draw.arc((x+3*s,y+12*s,x+21*s,y+28*s), 180, 360, fill=color, width=max(1,round(1.7*s)))
    elif name == "key":
        draw.ellipse((x+2*s,y+4*s,x+12*s,y+14*s), outline=color, width=max(1,round(1.7*s)))
        line([(10, 13), (20, 22), (23, 19), (20, 16)])
    elif name == "scan":
        for a,b,c in [((2,8),(2,2),(8,2)),((16,2),(22,2),(22,8)),((2,16),(2,22),(8,22)),((16,22),(22,22),(22,16))]:line([a,b,c])
        line([(7,12),(17,12)])
    elif name == "bath":
        line([(5,21),(5,8),(8,4),(14,4),(18,8)]);line([(12,10),(21,10)])
        for dx in (13,17,21):line([(dx,14),(dx,17)])
    elif name == "food":
        line([(5,2),(5,22)]);line([(2,2),(2,9),(8,9),(8,2)]);line([(18,2),(18,22)]);line([(18,2),(14,8),(18,11)])
    else:
        draw.ellipse((x+4*s,y+4*s,x+20*s,y+20*s), outline=color, width=max(1,round(1.7*s)))


def switch(draw, x, y, on=True):
    box(draw, (x,y,x+42,y+24), GREEN if on else LINE, 12)
    draw.ellipse((x+(21 if on else 3),y+3,x+(39 if on else 21),y+21), fill=CARD)


class ScaledDraw:
    def __init__(self, draw, scale): self.draw,self.scale=draw,scale
    def coordinates(self, values):
        return [(x*self.scale,y*self.scale) for x,y in values] if isinstance(values[0],(tuple,list)) else tuple(v*self.scale for v in values)
    def text(self,xy,value,**kwargs):
        kwargs['font']=kwargs['font'].font_variant(size=kwargs['font'].size*self.scale)
        return self.draw.text(self.coordinates(xy),value,**kwargs)
    def __getattr__(self,name):
        def call(bounds,*args,**kwargs):
            if 'width' in kwargs: kwargs['width']=max(1,round(kwargs['width']*self.scale))
            elif name in ('line','arc'): kwargs['width']=self.scale
            if 'radius' in kwargs: kwargs['radius']*=self.scale
            return getattr(self.draw,name)(self.coordinates(bounds),*args,**kwargs)
        return call


def navigation(draw, selected):
    for n,(label,name) in enumerate([("首页","home"),("校园卡","card"),("账单","bills"),("我的","person")]):
        x=45+90*n
        if n==selected:box(draw,(x-25,699,x+25,727),"#E8ECE3",14)
        icon(draw,name,x-10,703,20,GREEN if n==selected else MUTED)
        text(draw,(x,731),label,11,GREEN if n==selected else MUTED,anchor="mt")


@lru_cache(maxsize=5)
def screen(kind):
    image=Image.new("RGB",(1080,2340),PAPER)
    d=ScaledDraw(ImageDraw.Draw(image),3)
    text(d,(22,13),"9:41",11,bold=True)
    box(d,(309,16,328,25),None,2,INK);d.line((330,18,330,23),fill=INK);box(d,(312,19,324,22),INK,1)
    title={"home":"泥浆生活","bills":"账单","card":"校园卡","codes":"使用码","accounts":"欢迎使用泥浆生活"}[kind]
    if kind=="codes":text(d,(22,57),"‹",27);text(d,(49,57),title,25)
    else:text(d,(22,57),title,25,bold=False)
    if kind=="bills":
        box(d,(22,112,338,212));text(d,(42,128),"校园卡余额",12,MUTED)
        text(d,(42,151),"¥ 86.50",30,bold=True);box(d,(260,150,318,186),"#E8ECE3",10);text(d,(275,159),"充值",13,GREEN)
        text(d,(23,244),"2026年 10月",14);d.line([(125,252),(130,257),(135,252)],fill=MUTED)
        text(d,(336,232),"本月支出",11,MUTED,anchor="rt")
        text(d,(336,250),"¥ 128.60",22,bold=True,anchor="rt")
        x=22
        for n,label in enumerate(["全部","饮食","洗澡","充值","其他"]):
            box(d,(x,294,x+56,324),"#E8ECE3" if n==0 else PAPER,10,LINE)
            text(d,(x+28,301),label,12,GREEN if n==0 else MUTED,anchor="mt");x+=65
        text(d,(25,347),"2026-10-24",12,MUTED)
        for i,(title,kind,amount,hour) in enumerate([("第二食堂","food","−12.50","12:10"),("洗浴消费","bath","−2.40","08:20"),("支付宝充值","card","+100.00","08:00")]):
            y=378+i*70;box(d,(22,y+6,60,y+44),CARD,12);icon(d,kind,31,y+15,20)
            text(d,(74,y+6),title,14,bold=True);text(d,(74,y+30),f"{['饮食','洗澡','充值'][i]} · {hour}",11,MUTED)
            text(d,(337,y+16),amount,15,MUTED if i==2 else INK,anchor="rt")
            d.line((73,y+66,338,y+66),fill=LINE)
        navigation(d,2)
    elif kind=="card":
        text(d,(180,117),"付款码",14,GREEN,anchor="mt")
        box(d,(53,151,307,405),"#FFFFFF",16)
        for x,y,flipx,flipy in [(76,174,1,1),(284,174,-1,1),(76,382,1,-1),(284,382,-1,-1)]:
            d.line([(x,y+22*flipy),(x,y),(x+22*flipx,y)],fill=LINE,width=4)
        icon(d,"card",157,224,46,MUTED);text(d,(180,293),"付款码已隐藏",17,MUTED,anchor="mt")
        text(d,(180,427),"在线付款码",12,MUTED,anchor="mt")
        text(d,(24,481),"启动时打开付款码",13);switch(d,293,477,False)
        d.line((24,522,336,522),fill=LINE)
        text(d,(24,547),"出示时提亮并保持亮屏",13);switch(d,293,543,False)
        navigation(d,1)
    elif kind=="home":
        for i,(label,name,fill) in enumerate([("扫码出水","scan",GREEN),("使用码","key",CARD)]):
            x=22+164*i;box(d,(x,117,x+152,171),fill,16)
            icon(d,name,x+19,133,22,CARD if i==0 else GREEN);text(d,(x+52,134),label,14,CARD if i==0 else GREEN)
        text(d,(22,205),"常用饮水机",16,bold=True);box(d,(22,242,338,397))
        for i,(label,mode) in enumerate([("演示设备 · 冷水","冷水"),("演示设备 · 热水","热水")]):
            y=259+i*74;icon(d,"card",39,y+5,23)
            text(d,(78,y),label,14,bold=True);text(d,(78,y+25),"已保存设备",11,MUTED)
            box(d,(276,y+6,322,y+38),"#E8ECE3",10);text(d,(299,y+13),"出水",12,GREEN,anchor="mt")
            if i==0:d.line((40,y+59,320,y+59),fill=LINE)
        navigation(d,0)
    elif kind=="codes":
        for i,(label,name) in enumerate([("洗浴使用码","bath"),("吹风机使用码","key")]):
            y=116+i*188;box(d,(22,y,338,y+158),CARD,18,LINE)
            icon(d,name,40,y+21,18);text(d,(68,y+22),label,13,MUTED,bold=True)
            text(d,(316,y+22),"修改" if i==0 else "更换新码",12,GREEN,anchor="rt")
            text(d,(42,y+50),"•••• ••••",31,bold=True)
            text(d,(42,y+88),"使用码已隐藏",11,MUTED)
            d.line((40,y+112,320,y+112),fill=LINE)
            text(d,(42,y+130),"洗浴键盘码" if i==0 else "吹风机键盘码",12,MUTED)
            switch(d,275,y+124,True)
    else:
        text(d,(22,110),"按需登录，随时可以回来补充。",13,MUTED)
        box(d,(22,157,338,388),CARD,20)
        for i,(label,purpose,name) in enumerate([("学校账号","付款码 · 余额 · 账单 · 电费","person"),("趣智校园","饮水机 · 吹风机使用码","card"),("趣智热水后勤","洗浴使用码","bath")]):
            y=176+i*74;icon(d,name,39,y+6,23);text(d,(77,y),label,14,bold=True)
            text(d,(77,y+27),purpose,11,MUTED);text(d,(317,y+11),"去登录 ›",11,GREEN,anchor="rt")
            if i<2:d.line((39,y+60,321,y+60),fill=LINE)
        box(d,(22,416,338,461),GREEN,13);text(d,(180,428),"稍后登录",14,CARD,anchor="mt")
    return image


@lru_cache(maxsize=1)
def casing():
    scale=3
    width=(PHONE_WIDTH+FRAME_PAD*2)*scale
    height=(PHONE_HEIGHT+FRAME_PAD*2)*scale
    body=Image.new('RGBA',(width,height));d=ImageDraw.Draw(body)
    top,bottom=(255,249,235),(202,185,156)
    for y in range(height):
        t=y/(height-1)
        color=tuple(round(a+(b-a)*t) for a,b in zip(top,bottom))
        d.line((0,y,width,y),fill=color+(255,))
    mask=Image.new('L',body.size)
    box(ImageDraw.Draw(mask),(0,0,width-1,height-1),255,46*scale)
    body.putalpha(mask)
    for inset,color,stroke in [(0,'#C7B89F',1),(2,'#FFFCF4',2),(5,'#DFD0B5',3),(8,'#FFF9EF',2)]:
        box(d,(inset*scale,inset*scale,width-1-inset*scale,height-1-inset*scale),None,
            (46-inset)*scale,color,stroke*scale)
    return body.resize((width//scale,height//scale),Image.Resampling.LANCZOS)


def phone(canvas, kind, x, y=PHONE_TOP):
    width,h,pad=PHONE_WIDTH,PHONE_HEIGHT,FRAME_PAD
    shadow=Image.new("RGBA",canvas.size)
    box(ImageDraw.Draw(shadow),(x-pad,y+8,x+width+pad,y+h+28),(108,93,66,55),46)
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(19)))
    canvas.alpha_composite(casing(),(x-pad,y-pad))
    art=screen(kind).resize((width,h),Image.Resampling.LANCZOS)
    mask=Image.new("L",art.size);box(ImageDraw.Draw(mask),(0,0,width-1,h-1),255,33)
    canvas.paste(art,(x,y),mask)


def background(size=POSTER_SIZE):
    canvas=Image.new('RGBA',size)
    d=ImageDraw.Draw(canvas)
    for y in range(size[1]):
        t=y/(size[1]-1)
        color=tuple(round(a+(b-a)*t) for a,b in zip((255,252,243),(242,242,230)))
        d.line((0,y,size[0],y),fill=color+(255,))
    width,height=size
    folds=Image.new('RGBA',size);f=ImageDraw.Draw(folds)
    f.polygon([(0,height*.92),(width,height*.30),(width,height),(0,height)],fill=(200,212,184,48))
    f.polygon([(0,height),(width,height*.67),(width,height)],fill=(187,203,170,35))
    canvas.alpha_composite(folds)
    return canvas


def footer(draw, width, height):
    text(draw,(70,height-52),"演示数据",18,MUTED)
    text(draw,(width-70,height-52),"MudLife",18,GREEN,anchor="rt")


def social_poster(output):
    import qrcode
    size=(1080,3800)
    poster=background(size);d=ImageDraw.Draw(poster)
    brand=Image.open(ROOT/'assets/mudlife-icon-preview.png').convert('RGBA').resize((88,88),Image.Resampling.LANCZOS)
    poster.alpha_composite(brand,(80,65))
    text(d,(194,74),'泥浆生活',34,GREEN,bold=True)
    text(d,(196,122),'MudLife',22,MUTED)
    text(d,(80,222),'校园日常，',66,GREEN,bold=True)
    text(d,(80,310),'放在一起。',66,GREEN,bold=True)
    text(d,(82,413),'校园卡付款 · 生活账单 · 饮水与使用码',27,MUTED)
    phone(poster,'bills',140,510);phone(poster,'card',610,510)

    text(d,(80,1336),'顺手使用，少走几步',46,GREEN,bold=True)
    text(d,(82,1405),'常用饮水机，洗浴与吹风机使用码。',25,MUTED)
    phone(poster,'home',140,1500);phone(poster,'codes',610,1500)

    text(d,(80,2325),'按需登录，轻松开始',46,GREEN,bold=True)
    text(d,(82,2394),'三个账号独立保存，随时可以回来补充。',25,MUTED)
    phone(poster,'accounts',140,2500)
    for i,(label,description,name) in enumerate([
        ('学校账号','付款码、余额、账单\n充值、电费与电子学生卡','person'),
        ('趣智校园','常用饮水机\n吹风机使用码','card'),
        ('趣智热水后勤','洗浴使用码','bath')]):
        y=2570+i*196
        icon(d,name,594,y+4,28)
        text(d,(640,y),label,29,GREEN,bold=True)
        for line_index,line in enumerate(description.split('\n')):
            text(d,(640,y+54+line_index*35),line,23,MUTED)

    d.line((80,3320,1000,3320),fill=LINE,width=2)
    text(d,(80,3390),'把校园日常，装进口袋。',35,GREEN,bold=True)
    text(d,(82,3461),'江苏大学校园生活客户端',24,MUTED)
    text(d,(82,3511),'Android 8.0+ · Gitee 国内下载',24,GREEN)
    text(d,(82,3573),'gitee.com/cainite/MudLife',23,MUTED)
    text(d,(82,3620),'GitHub：Cainite07 / MudLife',22,MUTED)
    url='https://gitee.com/cainite/MudLife/releases'
    qr=qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_M,box_size=6,border=4)
    qr.add_data(url);qr.make(fit=True)
    code=qr.make_image(fill_color=GREEN,back_color='white').convert('RGBA')
    poster.alpha_composite(code,(744,3410))
    text(d,(744+code.width/2,3410+code.height+12),'扫码下载',22,GREEN,anchor='mt')
    footer(d,*size)
    target=output/'mudlife-social.png'
    poster.convert('RGB').save(target,optimize=True)
    print(f'Exported {target.name}: {size[0]}x{size[1]}, fixed device size354x739; QR={url}')


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--final',action='store_true',help='Export the approved fixed template to assets/promo')
    parser.add_argument('--social',action='store_true',help='Export only the continuous social poster')
    args=parser.parse_args()
    if not FONT.exists():raise SystemExit("请配置 Microsoft YaHei 字体路径。")
    output=ROOT/'assets/promo' if args.final else OUT
    output.mkdir(parents=True,exist_ok=True)
    if args.social:
        social_poster(output)
        return
    cover=background();d=ImageDraw.Draw(cover)
    brand=Image.open(ROOT/'assets/mudlife-icon-preview.png').convert('RGBA').resize((96,96),Image.Resampling.LANCZOS)
    cover.alpha_composite(brand,(82,70));text(d,(202,86),"泥浆生活",34,GREEN,bold=True);text(d,(204,134),"MudLife",20,MUTED)
    text(d,(82,292),"校园日常",70,GREEN,bold=True);text(d,(82,394),"放在一起",70,GREEN,bold=True)
    text(d,(85,522),"付款、账单、饮水与使用码",27,MUTED)
    for i,label in enumerate(["温暖纸色","轻触反馈","账号独立保存"]):
        x=85+i*174;box(d,(x,610,x+155,654),"#E8ECE3",22);text(d,(x+77,621),label,18,GREEN,anchor="mt")
    phone(cover,'bills',785);phone(cover,'card',1195)
    footer(d,*POSTER_SIZE);cover.convert('RGB').save(output/'overview.png',optimize=True)
    billing=background();d=ImageDraw.Draw(billing)
    text(d,(76,52),"生活账单，一目了然",54,GREEN,bold=True)
    text(d,(80,125),"余额在上方，饮食与洗浴开支在下方。",26,MUTED)
    for i,(name,title,description) in enumerate([('card','余额与充值','校园卡余额，集中查看'),('food','分类看流水','饮食、洗澡、充值，按月整理'),('bills','只统计支出','充值保留明细，不计入消费')]):
        y=298+i*142
        box(d,(82,y,160,y+78),'#E8ECE3',39);icon(d,name,104,y+22,34)
        text(d,(192,y+1),title,29,GREEN,bold=True);text(d,(192,y+47),description,24,MUTED)
    billing.alpha_composite(brand.resize((72,72),Image.Resampling.LANCZOS),(82,790))
    text(d,(175,802),'泥浆生活 · MudLife',24,GREEN)
    phone(billing,'bills',1110);footer(d,*POSTER_SIZE);billing.convert('RGB').save(output/'bills.png',optimize=True)
    daily=background();d=ImageDraw.Draw(daily)
    text(d,(76,52),"顺手使用，按需登录",54,GREEN,bold=True)
    text(d,(80,125),"常用饮水机、两组使用码，以及清晰的账号入口。",26,MUTED)
    for kind,x in [('home',150),('codes',635),('accounts',1120)]:phone(daily,kind,x)
    footer(d,*POSTER_SIZE);daily.convert('RGB').save(output/'daily.png',optimize=True)
    print(f'Exported 3 posters: {POSTER_SIZE}, each device {PHONE_WIDTH+FRAME_PAD*2}x{PHONE_HEIGHT+FRAME_PAD*2}px, shared top={PHONE_TOP-FRAME_PAD}px.')


if __name__=='__main__':main()
