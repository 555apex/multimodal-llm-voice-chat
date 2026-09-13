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


def _sync_flow_legacy():
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
    image.save(ROOT / "current-sync-flow-legacy.png", quality=95)


def sync_flow():
    image, draw = canvas(2500, 2540, "RoadAgent 业务代码同步、构建与运行目录映射", "节点已明确区分：文件夹、文件、Git 分支、Docker 镜像、容器和外部数据库")

    box(draw, (45, 155, 460, 310), "【Git 分支，不是文件夹】\nGitHub\nversion/roadagent-v1", "#E8F2FF")
    box(draw, (565, 140, 1135, 325), "【Windows 文件夹：上游业务仓库】\nC:\\Users\\ruixuanhu\\Desktop\\S534_DXG\\\nmultimodal-llm-voice-chat", "#E8F2FF")
    box(draw, (1240, 140, 1810, 325), "【Windows 文件夹：实际合并工作区】\nC:\\Users\\ruixuanhu\\Desktop\\S534_DXG\\\nroad-agent-merged", "#DDF7F2", "#208C7A")
    box(draw, (1915, 125, 2455, 340), "【从 DGX 保留的目录/配置】\nfrontend/：数字人\nspeech-service/：ASR/TTS\nconfigs/、deploy/dgx/：模型与部署", "#FFF3D8", "#D79924")
    arrow(draw, (460, 232), (565, 232), "拉取")
    arrow(draw, (1135, 232), (1240, 232), "语义合并")
    arrow(draw, (1915, 232), (1810, 232), "保留并融合", "#D79924")

    box(draw, (45, 455, 780, 705), "【后端源码目录】\nroad-agent-domain/、road-agent-core/\nroad-agent-application/、road-agent-adapters/\nroad-agent-interface/、road-agent-boot/", "#FFFFFF")
    box(draw, (880, 455, 1615, 705), "【前端与数字人源码目录】\nfrontend/src/\nfrontend/public/\nfrontend/digital-human-demo.html", "#FFFFFF")
    box(draw, (1715, 455, 2450, 705), "【语音、模型与部署目录】\nspeech-service/\nconfigs/models.yaml\ndeploy/dgx/", "#FFFFFF")
    arrow(draw, (1500, 325), (410, 455))
    arrow(draw, (1525, 325), (1248, 455))
    arrow(draw, (1550, 325), (2080, 455))

    box(draw, (700, 825, 1800, 980), "【构建与验证文件】\npom.xml、各后端模块 src/test/、frontend/package.json、speech-service/tests/\n验证通过后才进入 DGX 主项目与 GitHub 交付分支", "#EDE7FF", "#7655B5")
    arrow(draw, (410, 705), (900, 825))
    arrow(draw, (1248, 705), (1248, 825))
    arrow(draw, (2080, 705), (1600, 825))

    box(draw, (230, 1100, 1270, 1270), "【DGX 文件夹：唯一 RoadAgent 源码根目录】\n/home/whtc/workspace/projects/road-agent-dgx\n内网与公网共用这里构建出的镜像", "#DDF7F2", "#208C7A")
    box(draw, (1570, 1110, 2270, 1260), "【Git 分支，不是文件夹】\nGitHub roadagent-v2\n保存合并后的完整工程", "#E8F2FF")
    arrow(draw, (950, 980), (750, 1100), "同步源码")
    arrow(draw, (1550, 980), (1900, 1110), "提交并推送")

    box(draw, (25, 1390, 595, 1570), "【后端构建文件】\ndeploy/dgx/Dockerfile.backend\nJava 产物：road-agent-boot/target/*.jar", "#FFFFFF")
    box(draw, (645, 1390, 1215, 1570), "【前端构建文件】\ndeploy/dgx/Dockerfile.frontend\n输入 frontend/；产物 frontend/dist/", "#FFFFFF")
    box(draw, (1265, 1390, 1835, 1570), "【语音构建文件】\nspeech-service/Dockerfile\n输入 speech-service/app/", "#FFFFFF")
    box(draw, (1885, 1390, 2475, 1570), "【运行编排文件】\ndeploy/dgx/compose.yaml\ndeploy/dgx/compose.public.yaml\ndeploy/dgx/.env", "#FFFFFF")
    for target in (310, 930, 1550, 2180):
        arrow(draw, (750, 1270), (target, 1390))

    box(draw, (25, 1665, 595, 1805), "【Docker 镜像，不是文件夹】\nroad-agent-dgx-backend\n标签：business-20260913", "#E8F2FF")
    box(draw, (645, 1665, 1215, 1805), "【Docker 镜像，不是文件夹】\nroad-agent-dgx-frontend\n标签：business-20260913", "#E8F2FF")
    box(draw, (1265, 1665, 1835, 1805), "【Docker 镜像，不是文件夹】\nroad-agent-dgx-speech\n标签：speech-20260910", "#E8F2FF")
    arrow(draw, (310, 1570), (310, 1665))
    arrow(draw, (930, 1570), (930, 1665))
    arrow(draw, (1550, 1570), (1550, 1665))

    box(draw, (25, 1900, 700, 2055), "【后端容器，不是文件夹】\nroad-agent-dgx-backend（内网）\nroad-agent-dgx-public-backend（公网）", "#EDF8FF")
    box(draw, (790, 1900, 1465, 2055), "【前端容器，不是文件夹】\nroad-agent-dgx-frontend → 127.0.0.1:18080\nroad-agent-dgx-public-frontend → 127.0.0.1:18081", "#EDF8FF")
    box(draw, (1555, 1900, 2175, 2055), "【共享语音容器】\nroad-agent-dgx-speech\n内外网后端共同调用", "#EDF8FF")
    arrow(draw, (310, 1805), (360, 1900))
    arrow(draw, (930, 1805), (1125, 1900))
    arrow(draw, (1550, 1805), (1865, 1900))
    draw.text((2180, 1605), "此 Compose 配置负责创建上面的全部内网/公网容器", font=SMALL, fill="#355B7D", anchor="mm")

    box(draw, (25, 2140, 785, 2325), "【外部数据库，不是文件夹】\nMySQL road_agent schema\n连接配置：deploy/dgx/.env\n及公网凭据文件", "#FFF3D8", "#D79924")
    box(draw, (855, 2115, 1680, 2340), "【独立 DGX 文件夹：LLM 服务】\n/home/whtc/workspace/projects/\nmodel-serving\n权重：/home/whtc/models/\nNVIDIA--Qwen3.6-35B-A3B--NVFP4", "#DDF7F2", "#208C7A")
    box(draw, (1750, 2115, 2475, 2340), "【DGX 模型文件夹】\nASR：/home/whtc/models/\nSystran--faster-whisper-small\nTTS：/home/whtc/models/\nQwen--Qwen3-TTS-12Hz-0.6B-CustomVoice", "#DDF7F2", "#208C7A")
    arrow(draw, (360, 2140), (360, 2055), color="#D79924")
    arrow(draw, (1265, 2115), (600, 2055), "model-serving-qwen")
    arrow(draw, (2110, 2115), (1865, 2055))
    box(draw, (130, 2410, 2370, 2505), "名称澄清：business-20260913 是镜像标签；public-backend/public-frontend 是 Compose 服务；roadagent-business 和 roadagent-public 不是当前源码根目录。", "#FDE9E7", "#C95A4A")
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
