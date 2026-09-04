# “路智通”卡通数字人姿态资产生成记录

## 1. 固定母版

- 身份母版：`digital-human-chibi-guardian-render.png`
- 母版尺寸：1024×1536，RGBA PNG
- 生成日期：2026-09-03
- 生产输出：`frontend/public/digital-human/guardian/`
- 完整尺寸、透明通道、文件大小与 SHA-256：见 `ASSET_MANIFEST.json`

`digital-human-chibi-guardian-checkerboard-preview.png` 仅作为人工预览参考，不进入前端运行资产。

## 2. 待命姿态提示词

```text
Edit the supplied transparent character render into the IDLE / standby pose for a production web UI asset.

IDENTITY LOCK — preserve exactly:
- the same chibi road-traffic guardian character, face shape and proportions
- large glossy blue water-wave hair with the same curls and white/cyan highlights
- the same orange lily flower, large blue eyes with bridge/road motifs
- the same blue/lime/white traffic uniform, pockets, shoes, translucent roadway-water wings
- the chest badge and its legible Chinese text “路智通” exactly unchanged
- the same camera, full-body scale, lighting direction, foot baseline and 1024×1536 canvas alignment

POSE CHANGE ONLY:
- front-facing neutral standby
- both arms and hands relaxed naturally at the sides, symmetric and anatomically correct
- friendly but restrained expression, soft closed-mouth smile
- eyes looking forward; attentive professional presence
- keep feet in exactly the same location and character centered exactly as the reference

OUTPUT REQUIREMENTS:
- transparent RGBA background, genuinely empty alpha outside the character
- no checkerboard baked into the image, no black/white/colored background, no floor, no shadow, no frame
- no new text, labels, logos, watermark, extra objects, extra limbs or malformed fingers
- crisp clean transparent edges, complete hair/flower/wings/shoes, nothing cropped
- maintain the original high-detail 3D illustration finish
- output only the edited character asset
```

## 3. 思考姿态提示词

```text
Edit the supplied transparent character render into the THINKING / traffic-analysis pose for a production web UI asset.

IDENTITY LOCK — preserve exactly:
- the same chibi road-traffic guardian character, face shape and proportions
- large glossy blue water-wave hair with the same curls and white/cyan highlights
- the same orange lily flower, large blue eyes with bridge/road motifs
- the same blue/lime/white traffic uniform, pockets, shoes, translucent roadway-water wings
- the chest badge and its legible Chinese text “路智通” exactly unchanged
- the same camera, full-body scale, lighting direction, foot baseline and 1024×1536 canvas alignment

POSE CHANGE ONLY:
- thoughtful traffic-analysis posture
- body remains front-facing and balanced
- one hand is gently raised near the chin in a natural thinking gesture; the other arm rests naturally at the side
- eyes glance subtly upward and to one side, not exaggerated
- calm focused expression with closed mouth
- anatomically correct hand and fingers
- keep feet in exactly the same location and character centered exactly as the reference

OUTPUT REQUIREMENTS:
- transparent RGBA background, genuinely empty alpha outside the character
- no checkerboard baked into the image, no black/white/colored background, no floor, no shadow, no frame
- no new text, labels, logos, watermark, extra objects, extra limbs or malformed fingers
- crisp clean transparent edges, complete hair/flower/wings/shoes, nothing cropped
- maintain the original high-detail 3D illustration finish
- output only the edited character asset
```

## 4. 讲解姿态

讲解姿态不重新生成，直接复用透明母版的开放手势，避免脸型、胸牌文字和人物比例产生额外漂移。

## 5. 透明资产处理与检查

图像生成结果的文件格式为 1024×1536 RGB PNG，并带有烘焙的浅色棋盘预览。生产资产未直接使用这些输出，处理步骤如下：

1. 使用本地 `u2net_human_seg` 人物分割模型保护白色鞋面和人物轮廓。
2. 使用四边连通的浅色背景遮罩严格移除人物及翅膀后方棋盘格；人物分割遮罩只用于恢复下方鞋靴区域，避免棋盘被误认为半透明翅膀。
3. 将三姿态统一到 1024×1536 RGBA 画布，顶部锚点 Y=16，脚底基线 Y=1518。
4. 输出无损 WebP 及优化 PNG 回退文件；运行期优先加载 WebP，失败时自动切换 PNG。
5. 检查透明通道、边界、胸牌“路智通”、手指、鞋面、发梢、花朵、翅膀和脚底基线。

资产准备脚本为 `scripts/prepare-digital-human-assets.py`。它仅用于制作静态资产，不是 Road Agent 或 DGX 容器的运行依赖。

## 6. 语音嘴型资产（2026-09-04）

讲解姿态继续作为唯一人物基准。图像生成只提供闭嘴和半开嘴的局部候选，不直接作为生产整图；张开嘴沿用原讲解图中的自然开口。

### 6.1 闭嘴候选提示词

```text
Edit the supplied “路智通” transparent chibi guardian image for a speech-animation mouth shape. Keep the entire character, face, eyes, eyebrows, nose, hair, orange flower, wings, uniform, badge text, hands, body, lighting, proportions, camera, transparent canvas and pixel alignment unchanged. Change only the mouth into a natural fully closed, relaxed speaking-rest shape with a small soft smile. Do not alter any pixels or facial features outside the immediate lip area. Preserve the same 1024×1536 transparent RGBA production asset, with no background, checkerboard, shadow, watermark, new text or crop.
```

### 6.2 半开嘴候选提示词

```text
Edit the supplied “路智通” transparent chibi guardian image for a speech-animation mouth shape. Keep the entire character, face, eyes, eyebrows, nose, hair, orange flower, wings, uniform, badge text, hands, body, lighting, proportions, camera, transparent canvas and pixel alignment unchanged. Change only the mouth into a natural gently half-open speaking shape, midway between closed and the existing open smile, with a small dark mouth opening and restrained expression. Do not alter any pixels or facial features outside the immediate mouth area. Preserve the same 1024×1536 transparent RGBA production asset, with no background, checkerboard, shadow, watermark, new text or crop.
```

### 6.3 固定区域合成与不变量

内置图像生成工具输出的两个候选为 1024×1535，并带有预览棋盘背景，因此不直接进入运行目录。`scripts/prepare-digital-human-mouth-assets.py` 按以下规则生成生产资产：

1. 仅在底部补一行全透明像素，恢复 1024×1536 画布，不做缩放。
2. 固定提取 `x=374–528、y=638–760` 的嘴部区域，以 14 px 羽化边缘合成回讲解母版。
3. 先根据候选嘴型范围修复母版旧嘴，再对候选进行局部肤色匹配，避免方框边缘和脸部色差。
4. 区域外像素逐像素保持与 `guardian-explaining.png` 完全一致，整图透明通道保持不变。
5. 输出 `closed / half / open` 三档同画布 PNG、无损 WebP 和归档 master PNG；生成源保存在 `mouth-sources/`，用于复现和审计。

文件尺寸、透明像素统计、ROI、不变量和 SHA-256 见 `ASSET_MANIFEST.json`。
