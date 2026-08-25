# “路智通”数字人候选图 V3 生成记录

三张 V3 均为同一综合交通智能助手的备选 IP。人物身份、姿态和画风继承 V2，仅更换服装。

- 图像生成：Codex 内置 `image_gen`，`identity-preserve`，每张 V2 是对应 V3 的唯一编辑目标。
- 图像规格：1024 × 1536 px、2:3、PNG、RGBA。
- 服装定位：智慧政务专业装；禁止警务、执法、军装和真实部门标识。
- 共同锚点：一条细青绿数据线和一个小橙色协同节点。
- 前端、状态接口和设计报告均未修改。

## 透明通道恢复

内置编辑器生成的服装与人物符合要求，但将透明棋盘格扁平化为 RGB。经用户明确授权，最终文件采用确定性 Alpha 恢复：

1. 从 V3 RGB 源图识别近中性的棋盘格背景候选：`min(R,G,B) >= 220` 且通道极差 `<= 18`。
2. 从画布角点提取连通的外部背景，并清除满足面积、亮度和中性色条件的内部背景间隙。
3. 使用 V2 Alpha 作为宽松空间先验，防止远离人物的噪点进入轮廓。
4. 轮廓向内收一像素，施加半径 `0.65` 的窄抗锯齿过渡；仅改变 Alpha，不重绘人物或服装。
5. `Alpha = 0` 的不可见背景 RGB 置零；所有可见人物和服装 RGB 来自选定的 `image_gen` 输出。

## A｜城市交通值守官 V3

- 文件：`candidate-a-city-traffic-duty-officer-v3.png`
- SHA-256：`97680AE5CFDE54C55C43ADAC3ACE29946B8325E666904F850AC8BE535BE4FD8F`

```text
Use case: identity-preserve
Asset type: Candidate A V3 production master for a web-based traffic-agent digital human
Input images: Image 1 is the sole edit target and identity anchor.
Primary request: Change only the clothing of the woman in Image 1. Replace the entire V2 outfit with a mature “Mid-Career Smart Government Integrated Coordination Outfit.” Preserve every non-clothing aspect exactly.

ABSOLUTE IDENTITY, POSE, AND COMPOSITION INVARIANTS:
Preserve exactly the same approximately 35-year-old East Asian woman’s facial identity, apparent age, skin tone, facial proportions, eyes, nose, lips, dark tied-back hairstyle and every hair contour, restrained friendly closed-mouth smile, gaze direction, calm trustworthy temperament, body shape, shoulder width, hands, fingers, two-hands-gently-joined standby pose, arm positions, slight three-quarter camera angle, three-quarter-body crop, character size, body placement, lighting direction, and refined semi-realistic 3D art style. Do not modify, redraw, beautify, age, rescale, reposition, or restyle her face, hair, skin, hands, pose, body, camera, or expression.

NEW CLOTHING — “Mid-Career Smart Government Integrated Coordination Outfit”:
Create a mature, approachable, long-duty professional coordination outfit suitable for ongoing integrated public-service operations, but explicitly not a police, enforcement, reception-window, departmental, or ceremonial uniform.

Outer jacket:
- Deep ink navy and restrained gray-blue palette.
- Near-symmetrical, calm, practical silhouette.
- Short minimal stand collar.
- Centered hidden front placket with no exposed zipper and no decorative buttons.
- Moderately structured shoulders without epaulettes.
- Straight body, relaxed professional fit, no waist cinching, no peplum, no fashion tailoring.
- Hip-length coordination jacket with clean, durable smart-fabric surfaces and subtle matte texture.
- Minimal seam architecture, intended to communicate steady coordination rather than fashion or authority.
- Underlayer: a simple light gray-blue professional shirt, visible only at the neckline and a narrow central opening; no tie, scarf, bow, or decorative blouse.

Shared brand anchors only:
- Exactly one extremely thin low-saturation teal data line integrated into one seam.
- Exactly one small orange coordination node, smaller than a fingertip.
- No additional colored strips, glowing elements, large graphics, roadway motifs, maps, arrows, signal icons, or decorative patterns.

Lower garment:
- Clean straight-leg professional trousers in matching deep ink navy or gray-blue.
- No cargo pockets, flap pockets, tactical panels, utility loops, skirts, or fashion detailing.

EXPLICITLY REMOVE FROM V2:
Remove all diagonal or wrap-style lapels and closures, asymmetric suit styling, ceremonial or gown-like feeling, fashion waist shaping, overlapping blazer panels, translucent shoulder decoration, frosted shoulder insert, long diagonal sash effect, and decorative asymmetric hem. The V3 jacket should feel grounded, near-symmetrical, mature, calm, and suitable for long-term duty.

TRANSPARENT BACKGROUND INVARIANT:
Keep the native 1024×1536, vertical 2:3 RGBA canvas and genuine transparency. Every pixel outside the exact physical silhouette of the woman and clothing must have alpha = 0. Skin, hair mass, hands, jacket, shirt, and trousers should be opaque; partial alpha only for necessary 1–3 pixel edge antialiasing and fine flyaway hairs. Do not render or bake a checkerboard. Remove any visible background, blue-black halo, backlight, glow, shadow, floor, fog, haze, vignette, gradient, atmosphere, or UI. Preserve the existing character scale and crop.

Constraints: change only clothing; one character only; no text, logo, watermark, real department mark, government emblem, police badge, shield, epaulette, armband, name tag, insignia, brand mark, high-saturation red, weapon, baton, hat, or extra object.
Avoid: police or traffic-police styling, law-enforcement uniform, reception-window uniform, ceremonial uniform, military styling, business suit lapels, fashion blazer, waist-cinched jacket, diagonal wrap front, translucent shoulder ornament, dress or gown styling, cargo trousers, cropped head, cropped hands, extra fingers, missing fingers, fused fingers, checkerboard pixels, black or white fringe.
```

## B｜道路数据研判师 V3

- 文件：`candidate-b-road-data-analyst-v3.png`
- SHA-256：`692E1ECC4B80EEF31EE34A335159E9833F380F911F4110C3BDF2F394BAB66454`

```text
Use case: identity-preserve
Asset type: Candidate B V3 native-transparent digital-human character master
Input images: Image 1 is the sole edit target. It already contains a native alpha channel; inherit and preserve that real transparency rather than depicting transparency.
Primary request: CHANGE ONLY THE CLOTHING. Keep the exact person and replace only the V2 fashion outfit with a mature administrative technology expert outfit.
Identity lock: preserve exactly the same approximately 40-year-old East Asian male face, age texture, skin, eyeglasses, frame shape, hairstyle, hairline, expression, gaze, head angle, neck, body build, shoulders, arms, naturally lowered hands, finger anatomy, neutral standby pose, scale, centering, three-quarter-length crop, 1024 × 1536 2:3 composition, upper-front-left light, and premium restrained 2D editorial cel-shaded illustration style. Do not alter any non-clothing pixel or feature.
Remove completely: V2 turtleneck/high collar, sleeveless draped gilet, designer-vest feeling, asymmetrical wrap panels, and fashion-forward crossover construction.
New outer clothing: charcoal blue-gray mature administrative technical jacket; short clean stand collar; long sleeves; hidden front closure; slightly relaxed straight body; understated smooth front; matte refined technical woven fabric; no visible zipper teeth, buttons, lapels, shoulder decoration, utility pockets, or uniform styling.
New inner clothing: fine-gauge knitted collared shirt in light gray or deep indigo, with a small soft fold-down collar visible at the neckline. It must not be a turtleneck, mock neck, crew-neck sweater, dress shirt, suit shirt, or tie.
New trousers: flat-front professional straight-leg trousers in charcoal navy, minimal seams and no cargo or patch pockets.
Brand anchor: exactly one extremely thin low-saturation teal data line and one small orange data node on the jacket; optional tiny area of extremely low-contrast micro-grid weave. No large road/map/data graphic.
Mood: mature, steady, experienced, technically professional, administratively competent, trustworthy with data, collaborative with systems. Not law enforcement, not public-counter uniform, not security, not military, not field workwear.
CRITICAL NATIVE ALPHA OUTPUT: output a true 32-bit RGBA PNG at 1024 × 1536. Preserve the source canvas’s genuine transparent background. Every pixel outside the physical person silhouette must have alpha 0; the person should be opaque except for a narrow antialiased silhouette edge. DO NOT render, paint, flatten, simulate, or bake a checkerboard or any white/gray/black/color background. No background layer, glow, aura, halo, backlight, shadow, fog, gradient, vignette, scene, panel, UI, text, logo, or watermark.
Forbidden: department insignia, police badge, shield, epaulettes, armband, name tag, government emblem, logo, text, high-saturation red, police/traffic-police/military/security cues, suit, blazer, tie, ordinary outdoor jacket, work uniform, cargo pants, large pockets, changed face, changed glasses, changed hair, changed hands, changed pose, extra fingers, cropped head, cropped hands.
```

## C｜福建山海智行者 V3

- 文件：`candidate-c-fujian-mountain-sea-traveler-v3.png`
- SHA-256：`E414C510EF3887211A85B9E77BD4688A7F8938FF87AFED4D53BA2873D94BB22F`

```text
Use case: identity-preserve
Asset type: Candidate C V3 production digital-human master
Input images: Image 1 is the sole edit target and the authoritative identity, pose, composition, lighting, style, and transparency anchor.

Primary request: Change ONLY the clothing of the person in Image 1. Replace the V2 asymmetric ceremonial-looking wrap outfit with a restrained “Young Digital Government Professional” outfit for a modern transportation informatization context.

Strict identity and image invariants — preserve exactly from Image 1:
- the same single 25–32-year-old East Asian gender-neutral young adult
- exact face and facial proportions, age, skin tone, short dark hairstyle, hair shape and strands
- exact calm friendly expression, eye direction and gaze
- exact body shape, shoulders, arms, both hands, finger positions and anatomy
- exact relaxed standby pose, slight three-quarter front orientation
- exact 1024×1536 2:3 three-quarter-body composition, crop, character size, centering, and safety margins
- exact studio light direction and modern East Asian 2.5D illustration style
- exact genuine transparent-background behavior
Do not modify, redraw, beautify, age, masculinize, feminize, reposition, resize, rotate, or relight the person. Do not change the background.

Change only the garments:
- Concept: youthful digital-government professional wear, gender-neutral, modern, open, collaborative, intelligent, and understated; appropriate for a creative professional in transportation digital services.
- Color: deep teal-gray smart textile as the main tone, slightly greener and grayer than navy; matte, refined, low contrast.
- Upper garment: clean short stand collar; hidden center-front placket; natural unpadded shoulder line; straight tailored silhouette that follows the body comfortably but does not cinch the waist; hip-length; no diagonal wrap front, no ceremonial overlap, no lapels, no blazer construction, no robe language.
- Construction: minimal engineered seam architecture, precise but quiet; no visible buttons, tactical closures, straps, or utility hardware.
- Inner layer: only a small controlled amount of porcelain white visible at the collar and perhaps one narrow lower-layer reveal.
- Trousers: modern straight-leg trousers of medium width, simple uninterrupted front, matching deep teal-gray; no cargo pockets, flap pockets, tactical panels, sporty piping, or exaggerated volume.
- Regional/digital detail: use low-contrast embossed Fujian mountain–sea road-network texture only on one or two small garment panels; abstract and subtle, never literal scenery or a road map.
- Shared brand anchors: exactly one ultra-thin teal data line and exactly one tiny orange collaboration node, both integrated into a seam; no other bright accents.
- Overall impression: youthful but not idolized; innovative but not theatrical; digital, competent, open, collaborative, restrained.

Transparency/output requirements:
- native 1024×1536 PNG with a real RGBA alpha channel
- retain the true transparent background from Image 1
- all pixels outside the exact person silhouette must be alpha 0, with partial alpha only on a narrow antialiased edge
- subject remains opaque
- do NOT render, draw, bake, or simulate a checkerboard
- do NOT flatten onto white, gray, black, or any colored background
- no background scene, glow, halo, backlight, shadow, mist, vignette, gradient, ambient color cloud, or UI

Strict avoid: any change outside clothing; diagonal ceremonial wrap; hanfu; robe; cultural-creative souvenir styling; stage costume; anime idol styling; police, traffic police, military, or conventional department uniform; epaulettes; shoulder boards; badges; shields; armbands; name tag; government insignia; official seal; text; letters; logo; watermark; high-saturation red; literal mountains, ocean, coastline, or road map; business suit; lapels; tie; cargo pockets; tactical gear; props.
```
