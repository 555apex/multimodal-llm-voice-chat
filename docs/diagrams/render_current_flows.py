#!/usr/bin/env python3
"""Render the current RoadAgent architecture diagrams as PNG files."""

from pathlib import Path
from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parent
FONT_PATH = Path(r"C:\Windows\Fonts\msyh.ttc")


def font(size: int, bold: bool = False):
    preferred = Path(r"C:\Windows\Fonts\msyhbd.ttc") if bold else FONT_PATH
    return ImageFont.truetype(str(preferred if preferred.exists() else FONT_PATH), size)


TITLE = font(44, True)
SUBTITLE = font(23)
BODY = font(24)
SMALL = font(19)
BG = "#F3F8FF"
INK = "#16324F"
LINE = "#2878C8"


def canvas(width: int, height: int, title: str, subtitle: str):
    image = Image.new("RGB", (width, height), BG)
    draw = ImageDraw.Draw(image)
    draw.text((width // 2, 42), title, font=TITLE, fill="#0B3768", anchor="ma")
    draw.text((width // 2, 102), subtitle, font=SUBTITLE, fill="#58728C", anchor="ma")
    return image, draw


def box(draw, xy, text, fill="#FFFFFF", outline="#4C91D1", text_fill=INK, radius=18):
    draw.rounded_rectangle(xy, radius=radius, fill=fill, outline=outline, width=3)
    x1, y1, x2, y2 = xy
    draw.multiline_text(
        ((x1 + x2) // 2, (y1 + y2) // 2),
        text,
        font=BODY,
        fill=text_fill,
        anchor="mm",
        align="center",
        spacing=8,
    )


def arrow(draw, start, end, label=None, color=LINE, width=5):
    draw.line((start, end), fill=color, width=width)
    x1, y1 = start
    x2, y2 = end
    dx, dy = x2 - x1, y2 - y1
    length = max((dx * dx + dy * dy) ** 0.5, 1)
    ux, uy = dx / length, dy / length
    px, py = -uy, ux
    size = 15
    tip = (x2, y2)
    left = (x2 - ux * size - px * size * 0.65, y2 - uy * size - py * size * 0.65)
    right = (x2 - ux * size + px * size * 0.65, y2 - uy * size + py * size * 0.65)
    draw.polygon((tip, left, right), fill=color)
    if label:
        mx, my = (x1 + x2) // 2, (y1 + y2) // 2
        bbox = draw.textbbox((mx, my), label, font=SMALL, anchor="mm")
        draw.rounded_rectangle((bbox[0] - 8, bbox[1] - 4, bbox[2] + 8, bbox[3] + 4), 7, fill=BG)
        draw.text((mx, my), label, font=SMALL, fill="#355B7D", anchor="mm")


def sync_flow():
    image, draw = canvas(1900, 1560, "RoadAgent 当前业务代码同步与发布流程", "业务以上游分支为准，数字人与本地模型能力以 DGX 为准")
    box(draw, (55, 160, 420, 285), "GitHub 上游业务分支\nversion/roadagent-v1", "#E8F2FF")
    box(draw, (535, 160, 900, 285), "本地上游仓库\nmultimodal-llm-voice-chat", "#E8F2FF")
    box(draw, (1015, 160, 1380, 285), "本地合并工作区\nroad-agent-merged", "#DDF7F2", "#208C7A")
    box(draw, (1500, 145, 1845, 305), "DGX 保留能力\n数字人、Qwen3.6\nASR、TTS、部署配置", "#FFF3D8", "#D79924")
    arrow(draw, (420, 222), (535, 222), "拉取锁定提交")
    arrow(draw, (900, 222), (1015, 222), "语义合并")
    arrow(draw, (1500, 225), (1380, 225), "保留并融合", "#D79924")

    box(draw, (260, 420, 680, 550), "业务后端\n领域、CRUD、接口、数据库规则", "#FFFFFF")
    box(draw, (740, 420, 1160, 550), "DGX 前端\n业务页面与数字人交互", "#FFFFFF")
    box(draw, (1220, 420, 1640, 550), "本地模型适配\nthinking=false、ASR、流式 TTS", "#FFFFFF")
    arrow(draw, (1198, 285), (470, 420))
    arrow(draw, (1198, 285), (950, 420))
    arrow(draw, (1198, 285), (1430, 420))

    box(draw, (650, 665, 1250, 795), "构建、自动测试\n隔离数据库业务验证", "#EDE7FF", "#7655B5")
    arrow(draw, (470, 550), (780, 665))
    arrow(draw, (950, 550), (950, 665))
    arrow(draw, (1430, 550), (1120, 665))

    box(draw, (250, 920, 805, 1065), "DGX 主项目\n/home/whtc/workspace/projects/road-agent-dgx", "#DDF7F2", "#208C7A")
    box(draw, (1095, 920, 1650, 1065), "GitHub 最终交付分支\nroadagent-v2", "#E8F2FF")
    arrow(draw, (820, 795), (530, 920), "验证通过后同步")
    arrow(draw, (1080, 795), (1370, 920), "提交并推送")

    box(draw, (110, 1195, 440, 1305), "统一后端镜像", "#FFFFFF")
    box(draw, (610, 1195, 940, 1305), "统一前端镜像", "#FFFFFF")
    arrow(draw, (430, 1065), (275, 1195))
    arrow(draw, (625, 1065), (775, 1195))
    box(draw, (50, 1410, 360, 1505), "内网后端容器", "#EDF8FF")
    box(draw, (395, 1410, 705, 1505), "公网后端容器", "#EDF8FF")
    box(draw, (740, 1410, 1050, 1505), "内网前端容器", "#EDF8FF")
    box(draw, (1085, 1410, 1395, 1505), "公网前端容器", "#EDF8FF")
    box(draw, (1480, 1240, 1835, 1360), "共享业务数据库\n内外网后端共同访问", "#FFF3D8", "#D79924")
    arrow(draw, (275, 1305), (205, 1410))
    arrow(draw, (275, 1305), (550, 1410))
    arrow(draw, (775, 1305), (895, 1410))
    arrow(draw, (775, 1305), (1240, 1410))
    arrow(draw, (1480, 1285), (360, 1450), color="#D79924")
    arrow(draw, (1480, 1320), (705, 1450), color="#D79924")
    image.save(ROOT / "current-sync-flow.png", quality=95)


def access_flow():
    image, draw = canvas(2100, 1280, "RoadAgent 当前公网与内网访问链路", "公网入口已切换为 SakuraFrp 分配的 nyat.app HTTPS 地址")
    public = [(55, 170, 325, 285), (390, 170, 800, 285), (865, 170, 1185, 285), (1250, 170, 1590, 285), (1655, 170, 2045, 285)]
    texts = ["公网用户浏览器", "www-api-db.u4065293.nyat.app\nHTTPS :16194", "SakuraFrp 公网节点\n自动 HTTPS", "DGX frpc\n用户级 systemd 服务", "127.0.0.1:18081\n公网 Nginx / 前端"]
    fills = ["#E8F2FF", "#DDF7F2", "#DDF7F2", "#FFF3D8", "#E8F2FF"]
    for xy, text, fill in zip(public, texts, fills):
        box(draw, xy, text, fill)
    for left, right in zip(public, public[1:]):
        arrow(draw, (left[2], (left[1] + left[3]) // 2), (right[0], (right[1] + right[3]) // 2))

    box(draw, (1570, 390, 2045, 515), "公网后端\nroad-agent-dgx-public-backend", "#EDE7FF", "#7655B5")
    arrow(draw, (1850, 285), (1810, 410), "/api/v1")
    box(draw, (55, 455, 390, 570), "DGX / Tailnet\n内网用户", "#E8F2FF")
    box(draw, (500, 455, 850, 570), "127.0.0.1:18080\n内网 Nginx / 前端", "#E8F2FF")
    box(draw, (990, 450, 1450, 575), "内网后端\nroad-agent-dgx-backend", "#EDE7FF", "#7655B5")
    arrow(draw, (390, 512), (500, 512), "HTTP :18080")
    arrow(draw, (850, 512), (990, 512), "/api/v1")

    box(draw, (820, 690, 1280, 800), "共享后端依赖", "#FFFFFF", "#4C91D1")
    arrow(draw, (1810, 515), (1280, 745))
    arrow(draw, (1220, 575), (1050, 690))
    box(draw, (80, 925, 540, 1075), "共享业务数据库\n公网账号使用细粒度权限", "#FFF3D8", "#D79924")
    box(draw, (650, 925, 1070, 1075), "DGX 本地 Qwen3.6\nenable_thinking=false", "#DDF7F2", "#208C7A")
    box(draw, (1180, 925, 1620, 1075), "DGX 本地语音服务\nfaster-whisper + Qwen3-TTS", "#DDF7F2", "#208C7A")
    arrow(draw, (950, 800), (310, 925))
    arrow(draw, (1050, 800), (860, 925))
    arrow(draw, (1150, 800), (1400, 925))
    box(draw, (1690, 670, 2050, 790), "公网后端\n执行自动分类轮询", "#FDE9E7", "#C95A4A")
    box(draw, (1690, 900, 2050, 1020), "内网后端\n不执行自动分类轮询", "#EDF8FF")
    arrow(draw, (1810, 515), (1870, 670))
    arrow(draw, (1450, 512), (1870, 900))
    draw.text((70, 1195), "安全边界：公网仅暴露带登录认证的前端入口；数据库、Java 后端及本地模型端口不直接暴露。", font=SUBTITLE, fill="#355B7D")
    image.save(ROOT / "current-public-access-flow.png", quality=95)


if __name__ == "__main__":
    sync_flow()
    access_flow()
