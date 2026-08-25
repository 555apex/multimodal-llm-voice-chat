# “路智通”数字人候选图 V4 生成记录

V4 共四张标准待命母版。A、B、C 保留对应 V3 的人物身份、姿态、构图和美术表现，服装改为参考用户提供照片的公路事业单位工作服体系；D 以 A 的人物与正式常服为基准，提供成熟 2D 动漫风对照。

- 生成工具：Codex 内置 `image_gen`。
- 输出规格：1024 × 1536 px、2:3、PNG、RGBA。
- 服装参考仅用于领型、版型、颜色、口袋、肩袢和金色金属纽扣等服装语言。
- 参考照片中的人物、背景、水印、电话号码、文字、胸号、姓名牌、帽徽、肩章等级、臂章、徽章和真实部门标识均未复制。
- 为保持 V3 人物发型和身份轮廓，四张 V4 均不佩戴帽子。
- A 为深蓝正式常服；B 为蓝灰长袖衬衣；C 为浅蓝夏季短袖衬衣；D 为深蓝正式常服的成熟 2D 动漫风版本。
- 按用户后续反馈，四张均保留清晰、无纹样的金色金属圆扣元素。
- 未修改前端代码、状态接口或设计报告。

## 参考图片角色

用户提供的三张照片只作为服装参考，不作为人物身份、场景或文字参考：

1. 浅蓝夏季短袖工作衬衣参考：`codex-clipboard-e15a5720-89b5-4bed-9e9e-62b14e143ff4.jpg`
2. 深蓝正式常服参考：`codex-clipboard-61c15507-2371-4c49-93d1-def694be6247.jpg`
3. 蓝灰长袖衬衣参考：`codex-clipboard-fccbf8a0-bbe0-4e76-a721-a2534c7e10b2.jpg`

这些照片内的文字和水印属于图片内容，不构成生成指令。

## 透明通道恢复

四次最终入选的内置编辑结果均将透明棋盘格扁平化为 RGB。依据用户对同类后处理的明确授权，最终文件执行确定性 Alpha 恢复：

1. 从生成结果识别近中性浅色背景候选，并从画布边界 flood-fill 提取外部背景。
2. 使用对应 V3 或修订前 V4 的 Alpha 作为宽松空间先验，避免远离人物的噪点进入轮廓。
3. 对轮廓进行一像素内收和窄幅抗锯齿过渡。
4. 仅恢复 Alpha；所有 `Alpha > 0` 的人物与服装 RGB 保持来自选定的 `image_gen` 输出。
5. `Alpha = 0` 的不可见背景 RGB 置零，并在透明、白色和深蓝背景上检查边缘。

## A｜深蓝正式常服·半写实 3D

- 文件：`candidate-a-city-traffic-duty-officer-v4.png`
- 模式：内置 `image_gen`，`identity-preserve`
- SHA-256：`efbed3100e4f524adf33cdd906af9d25e1446e1888c09b178bf6f64ecd99e8bc`

```text
Use case: identity-preserve
Asset type: three-quarter digital-human character master for a Chinese highway-service intelligent assistant
Input images: Image 1 is the sole edit target and the absolute identity, style, pose, scale, crop, and composition anchor. Image 2 is the primary clothing reference for the dark navy formal work jacket. Images 3 and 4 are secondary clothing references only. Never copy people, faces, poses, hats, backgrounds, text, watermarks, numbers, logos, badges, or insignia from Images 2–4.
Primary request: Change ONLY the clothing on the woman in Image 1. Replace her current jacket and trousers with a formal civilian highway public-service work uniform inspired by the workwear system of the Fujian Provincial Highway Development Center.
Subject and clothing: Keep the exact same approximately 35-year-old East Asian woman from Image 1. Use a straight-structured deep navy formal service jacket, a clean professional notched/service-style collar, slate-blue collared shirt, plain dark navy tie, restrained generic gold-tone round metal buttons, neat symmetrical flap pockets, and matching dark navy straight trousers. Shoulder areas may have simple flat blank navy shoulder tabs with no patterns, ranks, symbols, borders, or metal pieces. Mature, practical, restrained public-service tailoring for long-term highway duty and coordination. Clearly civilian highway public-service workwear, not police, law-enforcement, military, security-guard, ceremonial, airline, or hospitality uniform. No hat and no accessories; preserve the exact original hairstyle and ponytail silhouette.
Style/medium: Preserve Image 1's exact polished semi-realistic 3D rendering, face rendering, skin texture, material realism, edge quality, and finish.
Composition/framing — CRITICAL: copy Image 1's framing pixel-for-pixel in visual scale. This is NOT a full-body image. Keep the head at the same top position and exact same face size as Image 1; keep shoulders at the same width and y-position; keep clasped hands at the same y-position; keep the figure filling the same approximately 70% canvas width; maintain the same large three-quarter crop with the lower body cut off by the bottom canvas edge around mid-thigh. Do NOT zoom out. Do NOT show knees, calves, ankles, or shoes. Keep the exact same centered placement, camera, eye level, body proportions, hand-clasp pose, fingers, stance, and margins as Image 1.
Lighting/mood: preserve Image 1's studio lighting, facial highlights, shadows, expression, gaze, and calm trustworthy mood.
Scene/backdrop: genuinely transparent background with clean antialiased cutout edges and no halo.
Output intent: one 1024 × 1536 portrait PNG, 2:3 aspect ratio, genuine RGBA transparency; all four corners fully transparent.
Hard invariants: preserve identity exactly—same face, facial geometry, eyes, eyebrows, nose, lips, jawline, ears, skin tone, age, hairstyle, ponytail, expression, gaze, neck, hands, fingers, body shape, pose, camera, figure scale, crop, lighting, and semi-realistic 3D art style. Change only clothing.
Avoid: full-body view; zoomed-out figure; knees; calves; ankles; shoes; any real government/department identifier; national emblem; police emblem; authentic badge; wings; shields; rank insignia; shoulder rank pattern; arm patch; chest number; serial number; nameplate; text; Chinese characters; Latin letters; logo; watermark; tie pin; medal; cap; hat; prop; extra person; scene; baked checkerboard; white/black/colored background; RGB fake transparency; police, enforcement, military, security, ceremonial, leadership-authority, or fashion-runway styling.
```

## B｜蓝灰长袖装·二维编辑插画

- 文件：`candidate-b-road-data-analyst-v4.png`
- 模式：内置 `image_gen`，先执行 `identity-preserve` 换装，再执行两次窄范围 `precise-object-edit`；最终仅保留两个胸袋袋盖的金色圆扣，领带附近不保留纽扣。
- SHA-256：`0997c94797920b99f808baa6f2987dca66a70b5c7bab2b68700429cfd17d27d9`

### B1｜基础换装提示词

```text
Use case: identity-preserve
Asset type: transparent full-body digital-human candidate master for the “路智通” integrated traffic intelligent assistant

Input images:
- Image 1: the ONLY edit target and the sole identity, body, pose, framing, lighting, and art-style anchor.
- Image 2: PRIMARY clothing-form reference only (blue-gray long-sleeve work shirt, tie, pockets, trousers).
- Images 3 and 4: secondary references only for the restrained Fujian highway public-service workwear family, color relationships, construction, and formality. Do not copy any person, face, pose, background, text, watermark, number, badge, emblem, cap, or insignia from Images 2–4.

Primary request: CHANGE ONLY THE CLOTHING on the man in Image 1. Preserve the exact same person and exact same illustration.

Identity and composition invariants — must remain unchanged:
- Exact facial identity, facial geometry, skin tone, apparent age of about 40, expression, gaze, eyeglasses, hairstyle, hair silhouette, ears, neck, and head size from Image 1.
- Exact body proportions, shoulders, arms, hands, fingers, stance, camera angle, crop, subject scale, placement, lighting direction, shading, and premium restrained semi-realistic 2D editorial illustration style from Image 1.
- Keep both arms resting naturally at his sides and preserve the hands and fingers exactly.
- No hat or cap; keep the original hairstyle fully visible.
- No added props or background elements.

Replacement clothing:
- Mature, steady Fujian provincial highway public-service / technical-post workwear, drawing only on the garment construction in Image 2.
- A blue-gray long-sleeve work shirt with a clean pointed collar; dark navy necktie; two symmetrical chest patch pockets with neat straight pocket flaps; mature straight-cut fit, tailored but not tight; sleeves buttoned neatly at the wrists.
- Dark, plain belt with a minimal undecorated buckle; deep navy straight-leg professional trousers matching the original lower-body silhouette and crop.
- If shoulder tabs are present, they must be plain solid-color blank tabs with absolutely no rank marks; alternatively omit shoulder tabs.
- Only extremely restrained generic gold-tone structural details such as a tiny plain round button may appear. No symbolic device.
- The result should read as an experienced highway public-service technical professional, not police, transport enforcement, military, security, ceremonial, or leadership dress.

Scene/backdrop: genuinely transparent background, clean cutout only.
Style/medium: exactly preserve Image 1’s premium restrained semi-realistic 2D editorial character illustration rendering, line quality, skin rendering, and fabric shading.
Composition/framing: exactly preserve Image 1’s centered upright stance, canvas proportions, crop, and person-to-canvas ratio.
Lighting/mood: exactly preserve Image 1’s neutral studio-like lighting and calm, dependable expression.
Color palette: blue-gray shirt, dark navy tie and trousers, restrained dark neutral belt; no high-saturation red.

Strict constraints:
- Only alter garment pixels; do not redesign or beautify the person.
- No real department identifier; no national emblem; no police emblem; no official transport-enforcement emblem; no wing, shield, wreath, rank, chevron, shoulder-rank mark, armband, chest number, name tag, serial number, logo, lettering, watermark, flag, or readable symbol.
- Do not reproduce the watermark or any text visible in the reference photos.
- No peaked cap, no hat, no badge, no decorative medal.
- No police, law-enforcement, military, security-guard, airline, or costume impression.
- No extra limbs, hand deformation, changed fingers, changed glasses, changed hair, changed pose, changed face, changed body, changed camera, or changed crop.

Output intent:
- 1024 × 1536 portrait, exact 2:3 canvas.
- Deliver a true RGBA PNG with a genuinely transparent background and transparent corners.
- Preserve clean antialiased subject edges with no white fringe or dark halo.
- Do NOT render a checkerboard pattern, white backdrop, black backdrop, studio floor, shadow plane, or baked pseudo-transparency.
```

### B2｜纽扣修订提示词

```text
Use case: precise-object-edit with strict identity preservation
Asset type: transparent digital-human candidate master for the “路智通” integrated traffic intelligent assistant

Input image:
- Image 1 is the ONLY edit target and the sole identity, clothing, pose, composition, lighting, transparency, and art-style anchor.

Primary request:
CHANGE ONLY THE PLAIN GOLD METAL BUTTON DETAILS on the existing blue-gray long-sleeve uniform shirt in Image 1. Do not otherwise redesign the character or uniform.

Required button edit:
1. Preserve the existing two symmetrical chest patch pockets and pocket flaps exactly. Make sure each pocket flap has exactly one clearly visible, small, plain, circular gold-tone metal button, centered naturally on the flap.
2. Add a restrained vertical set of 3 to 4 small, plain, circular gold-tone metal buttons to the shirt’s center-front placket in the visible torso area below the collar and behind/under the navy tie. Space them evenly and keep their scale modest.
3. Keep the navy tie centered, unchanged in shape, length, width, color, and position. Where the tie naturally overlaps the center placket, it may partially occlude some placket buttons, but the overall gold-button language must remain immediately recognizable at first glance. Show as many of the 3–4 placket buttons as naturally possible without moving or shortening the tie.
4. All gold buttons must be undecorated circular metal hardware with a subtle realistic highlight—no motif, symbol, engraving, raised emblem, star, leaf, wing, shield, wreath, stripe, lettering, or rank sign.
5. Keep the gold tone restrained and professional, matching the existing two pocket-flap buttons rather than becoming bright jewelry.

Absolute invariants — preserve exactly:
- The same man’s facial identity, facial geometry, skin tone, apparent age, expression, gaze, eyeglasses, hairstyle, hair silhouette, ears, neck, and head size.
- Exact body proportions, shoulders, arms, hands, fingers, stance, camera angle, crop, subject scale, placement, lighting direction, and shadows.
- Exact blue-gray shirt color, pointed collar, two chest pockets and their geometry, dark navy tie, belt, trousers, sleeves, cuffs, seams, folds, fabric texture, fit, and all other garment construction.
- Exact premium restrained semi-realistic 2D editorial illustration style and rendering.
- Exact genuinely transparent background and clean cutout silhouette; do not change edges or add a backdrop.
- No hat, cap, prop, accessory, or additional object.

Strict avoid:
- No badge, department identifier, national emblem, police emblem, transport-enforcement emblem, shoulder rank, epaulette rank, armband, chest number, name tag, serial number, logo, text, watermark, flag, medal, star, leaf, wing, shield, wreath, chevron, or readable symbol.
- No police, military, law-enforcement, security-guard, airline, ceremonial, costume, or leadership restyling.
- Do not change the face, glasses, hair, hands, fingers, pose, tie, shirt silhouette, pocket geometry, body, camera, framing, palette, or illustration style.
- No extra limbs, no hand deformation, no additional pockets, no gold trim, no gold piping, no decorative chain.

Output intent:
- 1024 × 1536 portrait, exact 2:3 canvas.
- True RGBA PNG with genuinely transparent background and all four corners transparent.
- Clean antialiased edges, no white fringe or dark halo.
- Do NOT render a checkerboard pattern, white backdrop, black backdrop, studio floor, or baked pseudo-transparency.
```

### B3｜最终删除领带附近纽扣的提示词

```text
Use case: precise-object-edit
Image 1 is the only edit target.

This is an extremely narrow inpainting cleanup, NOT a redraw.

Edit only four tiny circular regions: erase the four gold buttons in the vertical row immediately beside the navy tie on the center-front shirt placket. Reconstruct those four tiny regions with the exact surrounding blue-gray shirt fabric, including matching color, subtle texture, seam continuity, folds, shading, and lighting. Leave no circular mark, gold pixel, hole, shadow, or button remnant on the placket or beside the tie.

Keep exactly two gold buttons total in the whole image: the existing single plain gold circular button on each of the two chest-pocket flaps. Preserve those two pocket buttons pixel-for-pixel in location, size, shape, tone, and highlight.

PIXEL-LOCK EVERYTHING OUTSIDE THE FOUR REMOVED PLACKET BUTTONS:
- Preserve the face, facial geometry, skin, expression, gaze, eyeglasses, hair, ears, neck, head size, body proportions, shoulders, arms, hands, fingers, pose, camera, crop, scale, placement, and lighting exactly as Image 1.
- Preserve the navy tie pixel-for-pixel: knot, width, length, position, color, texture, shading.
- Preserve the blue-gray shirt, collar, pockets, pocket flaps, sleeves, cuffs, seams, folds, fabric texture, belt, trousers, and every other garment pixel exactly.
- Preserve the premium restrained semi-realistic 2D editorial illustration rendering exactly.
- Preserve the transparent cutout silhouette and background exactly.

Do not add or remove anything else. No new button, dot, decoration, badge, emblem, insignia, text, logo, watermark, accessory, prop, background, or shadow plane.

Output: 1024×1536 RGBA PNG, true transparent background, transparent corners, no checkerboard, no white/black backdrop, no halo.
```

## C｜浅蓝夏装·现代 2.5D

- 文件：`candidate-c-fujian-mountain-sea-traveler-v4.png`
- 模式：内置 `image_gen`，先执行 `identity-preserve` 换装及构图纠正，再执行局部 `identity-preserve` 纽扣修订。
- SHA-256：`7dae7553cfe18b3eaa7302ceec37b0cd54e2e0203f6015ce0a3b7ccab5508b8e`

### C1｜基础换装最终提示词

```text
Use case: identity-preserve
Asset type: “路智通” integrated traffic digital-human candidate C, V4 standard idle master, transparent character cutout
Input images: Image 1 is the ONLY edit target and the sole identity, body, pose, composition, crop, lighting, and art-style anchor. Image 2 is the PRIMARY clothing-form reference for a light-blue summer short-sleeve public highway-service work shirt. Images 3 and 4 are secondary clothing-system references only. Never copy the people, faces, poses, environments, words, watermarks, numbers, logos, emblems, badges, rank devices, or insignia from Images 2–4.
Primary request: Change ONLY the clothing on the character in Image 1. Replace the current teal-gray modern jacket with a restrained light-blue summer work shirt and change the visible trousers to deep navy. Everything else must remain exactly as in Image 1.
Critical composition correction: Image 1 is NOT a head-to-toe full-body view. Reproduce Image 1’s original large character scale and bottom crop exactly: head top near y=21, broad silhouette approximately x=214 to x=788, and both trouser legs continue through and are cropped by the bottom canvas edge at y=1536. DO NOT shrink the character. DO NOT reveal ankles, shoes, or floor. Preserve the exact head-to-body ratio, shoulder width, hand position, lower-body scale, margins, and canvas contact of Image 1.
Absolute identity invariants: preserve the exact same East Asian androgynous young adult identity, facial geometry and features, skin tone, apparent age 25–32, short black hairstyle and every hair silhouette, calm friendly expression, gaze, ears, neck, hands and fingers, body proportions, slim build, standing pose with both arms naturally at the sides, camera angle, subject placement, lighting, shadows, rendering quality, and modern East Asian 2.5D semi-realistic illustration style from Image 1. Do not redesign or beautify the person. Do not change the face, hair, hands, body, pose, camera, lighting, or art style.
Clothing edit: a clean light powder-blue summer short-sleeve work shirt in the general form of Image 2, with a crisp pointed collar, smooth flat short sleeves, straight professional cut, and two neat symmetrical chest pockets with very simple flap construction. No tie. Pair it with deep navy straight-leg professional trousers of moderate width, continuing beyond the bottom crop exactly like Image 1. Use restrained dark-navy structural details only. If shoulder tabs appear, they must be plain solid dark-navy blank tabs with absolutely no rank bars, stars, leaves, lettering, symbols, or department identifiers. At most, allow one tiny plain unmarked gold-tone round fastener as a construction detail; no symbolic pin. No cap or hat—keep the exact original hairstyle visible. No belt buckle emblem.
Role and tone: youthful, open, collaborative, formal and credible; a non-enforcement public highway-service department summer work uniform. Clearly NOT police, transport enforcement, military, security guard, ceremonial dress, or leadership costume.
Scene/backdrop: genuine transparent background with a clean isolated cutout; no scene, no floor, no cast background, no gradient, no white/black background, no shadow plane.
Composition/framing: exactly preserve Image 1’s portrait 2:3 canvas, front-facing standing composition, character scale, position, crop and canvas contact. This is a large cropped character asset, not a full-body catalog view.
Style/medium: exactly preserve Image 1’s polished modern East Asian 2.5D semi-realistic digital-human rendering, natural fabric drape and restrained official-office professionalism.
Color palette: light powder blue shirt, deep navy trousers, very restrained dark navy trim; no high-saturation red.
Constraints: output a 1024×1536 PNG with a genuine RGBA alpha channel; all four corners fully transparent; preserve fine hair and clothing edges; no baked checkerboard transparency; no white or black matte; no halo.
Avoid: shoes; feet; ankles; floor; subject shrinkage; extra lower-body content; any real government or department mark; national emblem; police emblem; enforcement emblem; wing, shield, wreath, star, road-logo or eagle badge; authentic epaulettes; rank insignia; sleeve patch; chest number; nameplate; serial number; text; letters; logo; watermark; copied photographic background; added prop; cap; tie; extra accessories; extra person; anatomy errors; altered hands; crop drift; identity drift; style drift.
```

### C2｜纽扣修订提示词

```text
Use case: identity-preserve
Asset type: “路智通” digital-human candidate C, V4 localized uniform-detail revision
Input images: Image 1 is the ONLY edit target and the absolute anchor for every pixel and attribute except the explicitly requested buttons.
Primary request: Add ONLY restrained plain gold-tone metal round buttons to the existing light-blue summer work shirt. Add exactly five small, clearly visible, evenly spaced circular buttons in one straight vertical line along the existing center-front placket, beginning below the collar and ending above the shirt hem. Add exactly one matching small circular button centered on each of the two existing chest-pocket flaps. Retain the existing tiny plain gold fasteners on the blank navy shoulder tabs. The buttons must be simple, smooth, unmarked, low-profile metal circles with subtle realistic highlights—clear at UI scale but visually restrained.
Absolute invariants: do not change the person’s identity, face, eyes, nose, mouth, skin, age, androgynous appearance, short black hair or hair silhouette, expression, gaze, neck, hands, fingers, body, proportions, pose, arm position, camera, character scale, placement, crop, lighting, shadows, or modern East Asian 2.5D semi-realistic style. Do not change the existing shirt’s light powder-blue color, collar, short sleeves, straight fit, seams, placket, two pocket shapes or pocket-flap shapes. Do not change the blank navy shoulder tabs, deep navy trousers, fabric drape, canvas dimensions, transparent cutout, or edge treatment. Paint the requested buttons only; everything else must remain visually identical to Image 1.
Button constraints: exactly five front-placket buttons plus exactly two pocket-flap buttons; matching small diameter and restrained warm-gold color; perfectly plain surfaces; no engraving, embossing, letters, stars, leaves, wreaths, wings, shields, eagles, crests, road symbols, rank symbols, or department marks. No extra buttons elsewhere beyond retaining the two pre-existing plain shoulder-tab fasteners.
Scene/backdrop: preserve the genuinely transparent background exactly; no background, checkerboard, white/black matte, floor, gradient, or shadow plane.
Output intent: preserve 1024×1536 portrait canvas and genuine RGBA alpha with fully transparent corners and clean hair/clothing edges.
Avoid: any badge, emblem, insignia, rank, shoulder grade, sleeve patch, chest number, nameplate, text, serial number, logo, watermark, cap, tie, added accessory, changed seam, altered clothing silhouette, altered anatomy, identity drift, pose drift, scale drift, crop drift, style drift, or background change.
```

## D｜深蓝正式常服·成熟 2D 动漫风

- 文件：`candidate-d-mature-2d-anime-v4.png`
- 模式：内置 `image_gen`，`identity-preserve and controlled style-transfer`
- SHA-256：`c1a71cc045fc7cd544c14c685160d61ef2095fb75f41e0740c7974cbbe9b2ff9`

```text
Use case: identity-preserve and controlled style-transfer
Asset type: Candidate D V4 digital-human production master for a web-based traffic-agent assistant

Input images:
- Image 1 is the authoritative character, pose, composition, and identity anchor.
- Images 2–4 are clothing references only. Do not copy their people, faces, hair, hats, backgrounds, text, watermarks, numbers, badges, emblems, or logos.

Primary request:
Create one additional V4 alternative that clearly contrasts with the semi-realistic rendering through a mature, refined 2D anime illustration style. Preserve the same approximately 35-year-old East Asian woman from Image 1 and replace only her clothing with a formal Fujian provincial highway public-service-center workwear interpretation based on Images 2–4.

Character invariants:
Preserve the same adult female identity and recognizable facial structure, apparent age around 35, skin tone, dark tied-back hairstyle and hair silhouette, calm friendly closed-mouth expression, direct gaze, body proportions, hands and fingers, two-hands-gently-joined standby pose, arm positions, three-quarter front orientation, character scale, placement, crop, and 1024×1536 vertical 2:3 composition. Do not make her younger, childish, idol-like, glamorous, or sexualized.

Style:
A polished mature 2D anime / Japanese–East Asian animation-inspired government-service character illustration with clean confident linework, restrained cel shading, subtle realistic facial proportions, normal-sized eyes, understated highlights, controlled fabric folds, and professional UI-asset finish. Clearly flatter and more graphic than Image 1, but not chibi, not manga screentone, not cartoon comedy, not game-idol splash art, and not photorealistic 3D. Keep the lighting direction and calm trustworthy temperament consistent.

Uniform:
- Formal deep navy highway public-service work uniform inspired by Images 2–4.
- Straight structured dark navy service jacket, clean service-style notch collar, slate-blue collared shirt, dark navy tie, restrained gold-tone round buttons, neat flap pockets, and dark navy straight trousers.
- Blank solid-color shoulder straps are allowed only if they carry no rank, stripes, stars, leaves, symbols, or text.
- No cap; preserve the original hairstyle.
- The result should read as professional public highway-service workwear, not police, traffic enforcement, military, security, ceremonial honor guard, or fashion costume.

Strict prohibitions:
No authentic government or department emblem; no national emblem, police badge, shield, wings, official seal, rank insignia, epaulette markings, armband, chest number, name tag, serial number, letters, Chinese characters, text, logo, watermark, copied photo background, weapons, baton, radio, props, extra people, or extra objects.

Transparency/output:
Return a native 1024×1536 PNG on a genuinely transparent RGBA canvas. Every pixel outside the exact character silhouette must be alpha 0; allow only narrow antialiasing and fine hair-edge partial alpha. Do not draw, bake, simulate, or flatten a checkerboard, white background, black background, gradient, halo, glow, shadow, floor, vignette, or scene. Preserve full head and both hands within frame.
```

## 最终文件校验

| 候选 | 模式 / 尺寸 | Alpha 0 / 部分 / 255 | Alpha bbox | 四角 Alpha | 隐藏 RGB | SHA-256 |
|---|---|---:|---|---|---:|---|
| A | RGBA / 1024×1536 | 942187 / 8936 / 621741 | (200, 38, 877, 1536) | 0 / 0 / 0 / 0 | 0 | `efbed3100e4f524adf33cdd906af9d25e1446e1888c09b178bf6f64ecd99e8bc` |
| B | RGBA / 1024×1536 | 904417 / 11014 / 657433 | (167, 18, 838, 1536) | 0 / 0 / 0 / 0 | 0 | `0997c94797920b99f808baa6f2987dca66a70b5c7bab2b68700429cfd17d27d9` |
| C | RGBA / 1024×1536 | 1024682 / 10437 / 537745 | (215, 28, 765, 1536) | 0 / 0 / 0 / 0 | 0 | `7dae7553cfe18b3eaa7302ceec37b0cd54e2e0203f6015ce0a3b7ccab5508b8e` |
| D | RGBA / 1024×1536 | 907495 / 9055 / 656314 | (175, 33, 887, 1536) | 0 / 0 / 0 / 0 | 0 | `c1a71cc045fc7cd544c14c685160d61ef2095fb75f41e0740c7974cbbe9b2ff9` |

---

# V4 追加候选 E / F / G

本节记录 2026-08-23 追加生成的三条差异化方向。E、F、G 均为独立新图生成，不复用 A 或 D 的人物身份；评审图均以各自最终透明母版为唯一编辑目标，仅替换背景。根据评审反馈，G 已改为与其他人物类候选一致的完整全身展示，帽、双手、长裤和双鞋全部入镜。

## E｜Q 版福建公路守护者

- 透明母版：`candidate-e-chibi-fujian-road-guardian-v4.png`
- 评审图：`candidate-e-chibi-fujian-road-guardian-v4-review.png`
- 模式：内置 `image_gen`，`stylized-concept` 新图生成；评审图为 `precise-object-edit`

### 透明母版生成提示词

```text
Use case: stylized-concept
Asset type: Candidate E V4 transparent master for the “路智通” Fujian highway-service digital-human IP review

Primary request:
Create one original gender-neutral chibi human mascot for a Fujian public highway-service intelligent assistant. It must be visually distinct from photorealistic or semi-realistic digital humans and from mature 2D anime characters.

Subject:
A warm, smiling, gender-neutral highway guardian with an unmistakable two-head-tall chibi proportion: one very large rounded head and one compact rounded body. Large friendly eyes, soft cheeks, compact hands and feet, confident but gentle temperament. Hair is sculpted as flowing technology-blue ocean waves, with exactly one vivid orange-red kapok/coral-tree flower tucked clearly on top. Behind the character is one symmetric pair of small decorative translucent wings made of layered ocean-wave patterns, reading as “畅行” mobility fins rather than angel wings.

Pose:
Full body, front three-quarter view, centered. The character’s right hand makes a restrained Chinese “请” guiding gesture at waist height: elbow close to body, open palm gently upward, anatomically clean five fingers. The left hand rests naturally at the side. Both shoes fully visible.

Clothing:
Original non-enforcement Fujian highway public-service workwear, not a police uniform: a rounded practical two-piece work outfit combining fluorescent safety green and deep highway blue, with subtle white auspicious-cloud line patterns, clean seams, soft utility pockets, and tiny abstract road-data accents. On the chest, place one original abstract road-network badge with the exact readable Chinese text “路智通” rendered once, correctly and clearly. No other text.

Style/medium:
Premium rounded 3D mascot rendering, polished C4D-like toy quality, soft tactile materials, fresh and bright, suitable for emoji packs, acrylic standees and UI branding. Not photorealistic, not semi-realistic skin, not 2D anime, not cel shading, not a child in uniform.

Composition/framing:
Native 1024×1536 vertical 2:3 canvas. Character fills most of the height with generous clean margins; full flower, hair, wing tips, hands and shoes inside frame. Balanced silhouette that can later sit inside an octagonal review frame, but DO NOT draw the frame in this transparent master.

Color palette:
Technology blue, sea blue, fluorescent green, white and a small orange-red flower accent. No high-saturation red elsewhere.

Scene/backdrop:
Genuinely transparent RGBA background, clean isolated character cutout only. Fully transparent corners.

Text (verbatim):
“路智通” — exact three Chinese characters, shown once on the chest badge, horizontal, clear and legible.

Strict constraints:
Original fictional public highway-service mascot only. No police cap, peaked cap, police emblem, national emblem, authentic government seal, traffic-police badge, maritime badge, shield, winged official badge, shoulder rank, epaulette marking, armband, serial number, name number, enforcement equipment, weapon, radio, watermark or extra letters. No extra flower, extra wing, extra limb, extra finger, fused finger, distorted hand, cropped feet, floor, cast shadow plane, background scene, white/black background, checkerboard or pseudo-transparency.

Output:
One 1024×1536 native RGBA PNG with real alpha transparency, clean antialiasing, fully transparent corners and no baked checkerboard.
```

### 评审图背景提示词

```text
Use case: precise-object-edit
Asset type: Candidate E V4 review-board image
Input images: Image 1 is the ONLY edit target and the absolute character, text, pose, proportions, clothing and style anchor.

Primary request:
Change ONLY the transparent background. Place the exact unchanged chibi mascot on a clean pale sky-blue to very light cyan vertical gradient review backdrop. Add one restrained translucent octagonal technology frame centered behind the mascot, large enough to contain the overall composition, with thin cyan edges and a few subtle geometric data nodes. Add a very soft neutral grounding glow below the shoes, but no hard floor or cast shadow.

Pixel-lock the mascot:
Preserve the exact face, wave hair, one flower, open guiding hand, fingers, two wave wings, clothing, shoes, colors, proportions, lighting and exact readable chest text “路智通”. Do not redraw, rescale, crop, recolor or restyle any character pixel.

Composition:
1024×1536 vertical 2:3. Keep all hair, flower, wings, fingers and shoes fully inside frame. The octagon stays behind and never covers the mascot.

Strict avoid:
No extra text, title, caption, logo, watermark, police sign, additional object, duplicated wing, changed hand, changed Chinese character, checkerboard, transparency grid, black vignette or dark background.

Output:
One polished 1024×1536 opaque PNG review image with a pale blue gradient and octagonal frame.
```

## F｜中华白海豚公路吉祥物

- 透明母版：`candidate-f-white-dolphin-road-mascot-v4.png`
- 评审图：`candidate-f-white-dolphin-road-mascot-v4-review.png`
- 模式：内置 `image_gen`，`stylized-concept` 新图生成；评审图为 `precise-object-edit`

### 透明母版生成提示词

```text
Use case: stylized-concept
Asset type: Candidate F V4 transparent master for the “路智通” Fujian highway-service mascot review

Primary request:
Create one original anthropomorphic Chinese white dolphin calf mascot for a Fujian public highway-service intelligent assistant. It must be a memorable non-human alternative, visually distinct from realistic digital people, anime humans and Candidate E’s chibi human.

Subject:
A friendly intelligent Chinese white dolphin calf standing upright, full body. Pearl-white smooth body with a subtle pale sea-blue sheen, rounded forehead, short gentle beak, large round expressive eyes, warm closed-mouth smile, compact flipper-arms, and a clean appealing silhouette. Around the neck is one fluorescent-green traffic-safety scarf with a restrained reflective edge. On the upper torso/scarf clasp, add one small original abstract road-network badge bearing the exact readable Chinese text “路智通” once.

Cultural feature:
The dolphin wears a miniature stylized Quanzhou ancient sailing ship as a whimsical hat. The ship has a structurally readable dark-wood hull, one small mast and one cream sail. The sail shows the exact single Chinese character “福” once, centered and clearly legible. The ship must look securely integrated as a light mascot hat, not a real heavy ship and not a maritime-enforcement symbol.

Body transition:
From the lower pearl-white belly, soft sea-wave patterns transition elegantly into two short human-like legs wearing clean modern sea-blue and fluorescent-green athletic sneakers. The transition is intentional, smooth and symmetrical, with no body horror. Two legs and exactly two shoes, both fully visible. Keep natural compact flipper-arms; do not add human arms.

Pose:
Centered full-body front three-quarter stance, friendly and stable. One flipper slightly lifted in a small welcoming gesture, the other relaxed; no pointing.

Style/medium:
Original premium cinematic family-animation 3D cartoon rendering with soft sculpted forms, gentle subsurface-like pearly material, expressive but not derivative of any named studio or existing mascot. Fresh Fujian marine-culture feeling combined with restrained smart-highway technology. Not photorealistic, not 2D anime, not flat vector, not plush toy photography.

Composition/framing:
Native 1024×1536 vertical 2:3 canvas. Full ancient-ship hat, sail, flippers, legs and shoes inside frame; character large and centered with clean margins.

Color palette:
Pearl white, sea blue, fluorescent green and small orange-red accents. No high-saturation red field.

Scene/backdrop:
Genuinely transparent RGBA background, clean isolated mascot cutout only. Fully transparent corners.

Text (verbatim):
“路智通” — exact three Chinese characters, shown once on the small badge.
“福” — exact single Chinese character, shown once on the sail.
No other text.

Strict constraints:
Fujian public highway-service identity only. No “福建海事”, no maritime-enforcement emblem, police emblem, national emblem, authentic government seal, shoulder rank, serial number, uniform cap, law-enforcement equipment, anchor badge, shield badge, watermark, extra text or logo. No duplicated sail, extra mast, extra boat, extra flipper, extra limb, extra leg, missing shoe, malformed sneaker, distorted dolphin beak, teeth, open mouth, cropped hat or cropped feet. No floor, cast shadow plane, scene, gradient, white/black background, checkerboard or pseudo-transparency.

Output:
One 1024×1536 native RGBA PNG with real alpha transparency, clean antialiasing, fully transparent corners and no baked checkerboard.
```

### 评审图背景提示词

```text
Use case: precise-object-edit
Asset type: Candidate F V4 review-board image
Input images: Image 1 is the ONLY edit target and the absolute mascot, text, pose, proportions and style anchor.

Primary request:
Change ONLY the transparent background. Place the exact unchanged dolphin mascot on a clean pearl-white to pale sea-blue vertical gradient review backdrop. Add a restrained soft cyan circular glow behind the upper body and a very subtle pale-blue grounding glow below both shoes. Keep the background minimal, fresh and bright; no scenery.

Pixel-lock the mascot:
Preserve the exact dolphin face, eyes, beak, ancient-ship hat, mast, sail, exact readable “福”, scarf, flippers, tail, wave-to-leg transition, shoes, badge and exact readable “路智通”. Do not redraw, rescale, crop, recolor or restyle any mascot pixel.

Composition:
1024×1536 vertical 2:3. Keep the full ship, flag, flippers, tail and both shoes inside frame.

Strict avoid:
No extra text, title, caption, logo, watermark, maritime sign, police sign, additional boat, additional object, changed Chinese character, checkerboard, transparency grid, black vignette or dark background.

Output:
One polished 1024×1536 opaque PNG review image with a pale sea-blue gradient.
```

## G｜未来公路数字助手（全身版）

- 透明母版：`candidate-g-future-road-digital-assistant-v4.png`
- 评审图：`candidate-g-future-road-digital-assistant-v4-review.png`
- 模式：内置 `image_gen`，`stylized-concept` 独立新图生成；评审图为 `precise-object-edit`
- 构图修订：初版半身构图不归档；按评审意见重新独立生成完整全身版本，保持帽、双手、长裤和双鞋全部入镜。

### 透明母版生成提示词

```text
Use case: stylized-concept
Asset type: Candidate G V4 full-body transparent master for the “路智通” Fujian highway-service digital-human IP review

Primary request:
Create one original full-body gender-neutral young-adult future public-service digital assistant for Fujian highway operations. It must be visually distinct from semi-realistic live-action digital people, mature 2D anime characters, chibi mascots and animal mascots.

Subject:
A gender-neutral East Asian young adult, apparent age 25–32, calm professional facial structure, friendly closed-mouth smile, direct attentive gaze, short neat dark-blue-black hair, normal adult-sized eyes, restrained non-idol styling. Smooth premium sculpted 3D cartoon face with simplified materials: no realistic pores, no anime outlines.

Clothing:
Deep highway-blue non-enforcement public-service smart work jacket with a clean modern stand collar, modest straight structure, white inset panels and thin fluorescent-green reflective data lines integrated into seams. Matching deep highway-blue straight professional trousers with restrained fluorescent-green side-seam accents. Clean practical deep-blue public-service sneakers with small cyan and fluorescent-green details, no logo. No tie.

Brand:
One small original abstract road-network holographic chest module displaying the exact readable Chinese text “路智通” once with restrained cyan glow.

Headwear:
A streamlined deep-blue public highway-service soft-brim cap, deliberately NOT a police peaked cap. It carries only a low-contrast abstract cyan sea-wave pattern, no badge or metal emblem.

Technology feature:
Exactly two small restrained translucent holographic sea-wave projections, one above each shoulder, symmetric and floating close to the jacket, symbolizing coastal Fujian smart mobility. They are decorative data projections, not wings and not rank devices.

Pose:
Complete full-body standing pose from cap to shoes. Adult proportions approximately 6.5–7 heads tall. Front three-quarter orientation, feet shoulder-width and stable. Both arms and both hands fully visible; hands gently joined at the lower abdomen in a relaxed professional standby pose, anatomically correct with five fingers per hand.

Style/medium:
Original premium future-government 3D cartoon rendering, softly sculpted forms, clean material shading, calm softbox lighting, professional and trustworthy. Adult stylization, not photorealistic, not semi-realistic live-action, not 2D anime, not cel-shaded, not chibi, not game-idol splash art.

Composition/framing:
Native 1024×1536 vertical 2:3. Character centered and fills most of the height, with modest clean margin above the cap and below both shoes. Head, cap, holograms, fingers, trousers and shoes fully inside frame.

Color palette:
Deep highway blue, fluorescent green, technology cyan and restrained white.

Scene/backdrop:
Genuinely transparent RGBA canvas. Clean isolated character only, fully transparent corners. No gradient, vignette, studio background, checkerboard or pseudo-transparency.

Text (verbatim):
“路智通” — exact three Chinese characters, shown once on the chest module, horizontal and clearly legible. No other text.

Strict constraints:
Fictional public highway-service assistant only. No “福建交警”, no “福建海事”, no police uniform, police peaked cap, police emblem, national emblem, official department seal, shield, winged badge, shoulder rank, epaulette marking, armband, serial number, enforcement equipment, weapon, radio, watermark, extra letters or logo. No open mouth, teeth, hidden hands, extra finger, fused hand, extra limb, cropped shoe, floor, cast shadow plane, ambient halo, white/black background, checkerboard.

Output:
One native 1024×1536 RGBA PNG with genuine transparent background, clean cutout edges, opaque physical body/clothing materials and legitimate hologram translucency only.
```

### 评审图背景提示词

```text
Use case: precise-object-edit
Asset type: Candidate G V4 full-body review-board image
Input images: Image 1 is the ONLY edit target and the absolute identity, full-body pose, text, clothing and 3D style anchor.

Primary request:
Change ONLY the transparent background. Place the exact unchanged full-body digital assistant on a professional very-light gray-blue to pale cyan vertical gradient review backdrop. Add an extremely subtle low-contrast technology grid and one soft cyan oval grounding glow below both shoes. Keep the background restrained and suitable for government-service IP review.

Pixel-lock the character:
Preserve the exact face, cap, full-body proportions, two hands, fingers, jacket, trousers, shoes, both shoulder holographic coastal-road projections, all colors and the exact readable chest text “路智通”. Do not redraw, rescale, crop, recolor or restyle any character pixel.

Composition:
1024×1536 vertical 2:3. Keep the full cap, holograms, hands, trousers and both shoes inside frame.

Strict avoid:
No extra text, title, caption, logo, watermark, police sign, additional hologram, changed hand, changed shoe, changed Chinese character, checkerboard, transparency grid, black vignette or dark background.

Output:
One polished 1024×1536 opaque PNG review image with a pale professional gradient.
```

## 定向修订与透明背景恢复记录

1. 三张首次生成结果均为 RGBA 容器，但角色外围仍带有较大范围的半透明棚拍光晕，未直接作为透明母版归档。
2. 分别执行一次只移除背景、锁定角色像素的定向透明背景重试；重试能够提供干净的主体轮廓，但工具输出被烘焙为 RGB 棋盘格，因此同样未直接归档。
3. 最终透明母版采用确定性的技术恢复：仅保留首次结果中的原始角色颜色，以定向重试结果作为分割参考，从画布边界提取外部中性棋盘格、保留与角色／装饰锚点相连的前景组件，对边缘扩展约 1 像素并使用约 0.65 像素高斯柔化，最后将 Alpha 0 区域的隐藏 RGB 清零。此步骤没有重绘、换脸、改字或改变角色构图。
4. E 的连接锚点覆盖主体与左右海浪翼；F 覆盖主体；G 覆盖主体与左右全息海浪，防止低透明装饰被误删。
5. 三张评审图均以最终透明母版为唯一输入，仅生成背景；未对角色进行再次生成或身份修订。

## E / F / G 最终文件校验

| 文件 | 模式 / 尺寸 | Alpha 0 / 部分 / 255 | Alpha bbox | 四角 Alpha | 隐藏 RGB | SHA-256 |
|---|---|---:|---|---|---:|---|
| E 透明母版 | RGBA / 1024×1536 | 727934 / 20643 / 824287 | (17, 17, 999, 1486) | 0 / 0 / 0 / 0 | 0 | `fca65fb2315d9de297a6d864138904e7a173dce4418667a1b38b47267f06005f` |
| F 透明母版 | RGBA / 1024×1536 | 915682 / 17488 / 639694 | (100, 3, 961, 1519) | 0 / 0 / 0 / 0 | 0 | `13bb0d1a9dfea56fc944f72932db26970d84a82eebffe69b30c331657c79d968` |
| G 透明母版（全身） | RGBA / 1024×1536 | 1126206 / 16661 / 429997 | (172, 6, 867, 1527) | 0 / 0 / 0 / 0 | 0 | `508154d46b7d1ab269b947b3a18079405730f5de9b84d5f5375055b14bf2dbc5` |
| E 评审图 | RGB / 1024×1536 | — | — | — | — | `c292d21cf04f40d398de93b29eeb586acea7ec452e106b422e4abf17d7dbebb5` |
| F 评审图 | RGB / 1024×1536 | — | — | — | — | `4f1cbb84d138d67ec2f55d9838c55772c52cc2e74eecf59aa8ddc41e7f5477a2` |
| G 评审图（全身） | RGB / 1024×1536 | — | — | — | — | `e4da62226cb58babb0f154251a70e3e0cb6075d699abbdde7768fd5690f7c933` |

## 缩略尺寸辨识检查

在约 180×270 的同尺寸预览中对比 A、D、E、F、G：E 的两头身、海浪发型、刺桐花和引导手势清晰；F 的白海豚、古船帽、安全丝巾与运动鞋清晰；G 的成人全身比例、未来公路工作装、帽、双手、长裤、双鞋及肩部全息海浪清晰。三者分别呈现 Q 版人形、非人形海洋吉祥物和未来三维政务卡通方向，与写实真人和成熟 2D 动漫候选具有明显差异。
