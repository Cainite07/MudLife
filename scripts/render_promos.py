"""Render editable, privacy-safe base illustrations. Final AI artwork is in assets/promo/."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app" / "build" / "outputs" / "promo-drafts"
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


def screen(kind):
    image=Image.new("RGB",(1080,2340),PAPER)
    d=ScaledDraw(ImageDraw.Draw(image),3)
    text(d,(22,13),"9:41",11,bold=True)
    box(d,(309,16,328,25),None,2,INK);d.line((330,18,330,23),fill=INK);box(d,(312,19,324,22),INK,1)
    title={"home":"泥浆生活","bills":"账单","card":"校园卡","codes":"使用码","accounts":"欢迎使用泥浆生活"}[kind]
    if kind=="codes":text(d,(22,57),"‹",27);text(d,(49,57),title,25)
    else:text(d,(22,57),title,25,bold=False)
    if kind=="bills":
        box(d,(22,112,338,230));text(d,(42,132),"校园卡余额",12,MUTED)
        text(d,(42,155),"¥ 86.50",30,bold=True);box(d,(260,154,318,190),"#E8ECE3",10);text(d,(275,163),"充值",13,GREEN)
        text(d,(23,262),"2026年 10月",14);d.line([(125,270),(130,275),(135,270)],fill=MUTED)
        text(d,(336,250),"本月支出",11,MUTED,anchor="rt")
        text(d,(336,268),"¥ 128.60",23,bold=True,anchor="rt")
        x=22
        for n,label in enumerate(["全部","饮食","洗澡","充值","其他"]):
            box(d,(x,312,x+56,342),"#E8ECE3" if n==0 else PAPER,10,LINE)
            text(d,(x+28,319),label,12,GREEN if n==0 else MUTED,anchor="mt");x+=65
        text(d,(25,365),"2026-10-24",12,MUTED)
        for i,(title,kind,amount,hour) in enumerate([("第二食堂","food","−12.50","12:10"),("洗浴消费","bath","−2.40","08:20"),("支付宝充值","card","+100.00","08:00")]):
            y=396+i*73;box(d,(22,y+6,60,y+44),CARD,12);icon(d,kind,31,y+15,20)
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
        text(d,(180,323),"宣传图不含可用付款信息",11,MUTED,anchor="mt")
        text(d,(180,427),"在线付款码",12,MUTED,anchor="mt")
        text(d,(24,481),"启动时打开付款码",13);switch(d,293,477,False)
        d.line((24,522,336,522),fill=LINE)
        text(d,(24,547),"出示时提亮并保持亮屏",13);switch(d,293,543,False)
        navigation(d,1)
    elif kind=="home":
        for i,(label,name,fill) in enumerate([("扫码出水","scan",GREEN),("使用码","key",CARD)]):
            x=22+164*i;box(d,(x,117,x+152,179),fill,16)
            icon(d,name,x+19,137,22,CARD if i==0 else GREEN);text(d,(x+52,138),label,14,CARD if i==0 else GREEN)
        text(d,(22,213),"常用饮水机",16,bold=True);box(d,(22,250,338,421))
        for i,(label,mode) in enumerate([("演示设备 · 冷水","冷水"),("演示设备 · 热水","热水")]):
            y=269+i*82;icon(d,"card",39,y+5,23)
            text(d,(78,y),label,14,bold=True);text(d,(78,y+25),"已保存设备",11,MUTED)
            box(d,(276,y+6,322,y+38),"#E8ECE3",10);text(d,(299,y+13),"出水",12,GREEN,anchor="mt")
            if i==0:d.line((40,y+65,320,y+65),fill=LINE)
        navigation(d,0)
    elif kind=="codes":
        for i,(label,name) in enumerate([("洗浴使用码","bath"),("吹风机使用码","key")]):
            y=116+i*226;box(d,(22,y,338,y+198),CARD,18,LINE)
            icon(d,name,40,y+21,18);text(d,(68,y+22),label,13,MUTED,bold=True)
            text(d,(316,y+22),"修改" if i==0 else "更换新码",12,GREEN,anchor="rt")
            text(d,(42,y+62),"•••• ••••",31,bold=True)
            text(d,(42,y+103),"使用码已隐藏",11,MUTED)
            d.line((40,y+132,320,y+132),fill=LINE)
            text(d,(42,y+153),"洗浴键盘码" if i==0 else "吹风机键盘码",12,MUTED)
            switch(d,275,y+148,True)
    else:
        text(d,(22,110),"按需登录，随时可以回来补充。",13,MUTED)
        box(d,(22,157,338,418),CARD,20)
        for i,(label,purpose,name) in enumerate([("学校账号","付款码 · 余额 · 账单 · 电费","person"),("趣智校园","饮水机 · 吹风机使用码","card"),("趣智热水后勤","洗浴使用码","bath")]):
            y=180+i*84;icon(d,name,39,y+6,23);text(d,(77,y),label,14,bold=True)
            text(d,(77,y+27),purpose,11,MUTED);text(d,(317,y+11),"去登录 ›",11,GREEN,anchor="rt")
            if i<2:d.line((39,y+64,321,y+64),fill=LINE)
        box(d,(22,446,338,491),GREEN,13);text(d,(180,458),"稍后登录",14,CARD,anchor="mt")
    return image


def phone(canvas, kind, x, y, width=330):
    h=round(width*780/360); pad=12
    shadow=Image.new("RGBA",canvas.size)
    box(ImageDraw.Draw(shadow),(x-8,y+10,x+width+8,y+h+14),(40,48,42,25),38)
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(14)))
    d=ImageDraw.Draw(canvas)
    box(d,(x-pad,y-pad,x+width+pad,y+h+pad),"#C9BBA5",46)
    box(d,(x-pad+2,y-pad+2,x+width+pad-2,y+h+pad-2),"#FFF9EE",44)
    box(d,(x-7,y-7,x+width+7,y+h+7),"#E4D8C5",39)
    box(d,(x-4,y-4,x+width+4,y+h+4),"#F6F0E3",36)
    art=screen(kind).resize((width,h),Image.Resampling.LANCZOS)
    mask=Image.new("L",art.size);box(ImageDraw.Draw(mask),(0,0,width-1,h-1),255,33)
    canvas.paste(art,(x,y),mask)


def footer(draw, width, height):
    text(draw,(70,height-52),"演示数据",18,MUTED)
    text(draw,(width-70,height-52),"MudLife",18,GREEN,anchor="rt")


def main():
    if not FONT.exists():raise SystemExit("请配置 Microsoft YaHei 字体路径。")
    OUT.mkdir(parents=True,exist_ok=True)
    cover=Image.new("RGBA",(1600,1000),PAPER);d=ImageDraw.Draw(cover)
    brand=Image.open(ROOT/'assets/mudlife-icon-preview.png').convert('RGBA').resize((96,96),Image.Resampling.LANCZOS)
    cover.alpha_composite(brand,(82,85));text(d,(202,104),"泥浆生活",34,bold=True);text(d,(204,152),"MudLife",20,MUTED)
    text(d,(82,306),"校园日常",70,bold=True);text(d,(82,409),"放在一起",70,bold=True)
    text(d,(85,536),"付款、账单、饮水与使用码",27,MUTED)
    for i,label in enumerate(["温暖纸色","轻触反馈","账号独立保存"]):
        x=85+i*174;box(d,(x,624,x+155,668),"#E8ECE3",22);text(d,(x+77,635),label,18,GREEN,anchor="mt")
    phone(cover,'bills',790,165,315);phone(cover,'card',1167,125,315)
    footer(d,1600,1000);cover.convert('RGB').save(OUT/'overview.png',optimize=True)
    billing=Image.new('RGBA',(1080,1600),PAPER);d=ImageDraw.Draw(billing)
    text(d,(74,80),"生活账单，一目了然",54,bold=True);text(d,(78,167),"余额在上方，饮食与洗浴开支在下方。",27,MUTED)
    phone(billing,'bills',276,286,528);footer(d,1080,1600);billing.convert('RGB').save(OUT/'bills.png',optimize=True)
    daily=Image.new('RGBA',(1600,1180),PAPER);d=ImageDraw.Draw(daily)
    text(d,(76,62),"顺手使用，按需登录",58,bold=True);text(d,(80,151),"常用饮水机、两组使用码，以及清晰的账号入口。",27,MUTED)
    for kind,x in [('home',154),('codes',635),('accounts',1116)]:phone(daily,kind,x,275,330)
    footer(d,1600,1180);daily.convert('RGB').save(OUT/'daily.png',optimize=True)
    print('Rendered overview.png, bills.png and daily.png with fictional data only.')


if __name__=='__main__':main()
