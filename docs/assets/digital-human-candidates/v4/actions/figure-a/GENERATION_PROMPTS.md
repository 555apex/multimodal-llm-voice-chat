# Figure A 姿态资产生成记录

本目录以 V4 候选 A 为唯一人物、制服、画风、镜头和透明背景锚点。`idle.png` 直接沿用候选 A；`thinking.png` 与 `explaining.png` 由身份保持编辑生成，并经过仅针对袖口装饰的精确清理。

## 资产校验摘要

| 文件 | 规格 | 模式 | SHA-256 |
| --- | --- | --- | --- |
| `idle.png` | 1024×1536 | RGBA | `efbed3100e4f524adf33cdd906af9d25e1446e1888c09b178bf6f64ecd99e8bc` |
| `thinking.png` | 1024×1536 | RGBA | `d36d15bc2e0379341cb15cedd887e7c2fdca04513a0076354ca8221d358572ea` |
| `explaining.png` | 1024×1536 | RGBA | `c89324112c4355467a1e6350c707e78004df251f65382d286b64460deb0869be` |

三张图的四角 Alpha 均为 0；完全透明像素的 RGB 已归零。由于图像编辑输出阶段将透明预览烘焙成了中性棋盘格，最终资产使用确定性边界连通区域恢复 Alpha，并以原 V4 A 的轮廓作为保守先验；人物可见像素未进行重新绘制。

## Thinking：身份保持编辑提示词

```text
Use case: identity-preserve
Asset type: Figure A “thinking” pose master for a lightweight Vue digital-human state prototype
Input images: Image 1 is the ONLY edit target and the absolute identity, clothing, style, camera, lighting, scale, crop, and transparency anchor.

Primary request:
Change ONLY the body pose, hands, arms, and gaze of the woman in Image 1 to a restrained professional thinking / traffic-analysis pose. Keep the same person and the same uniform.

Required thinking pose:
- Raise the subject’s right hand (viewer’s left) naturally toward the lower chin; the thumb and bent index finger lightly touch or hover at the chin in a small, composed analysis gesture.
- Keep the right elbow close to the torso, not flared outward.
- Place the subject’s left hand naturally and visibly across the lower waist/abdomen area, relaxed and not gripping the opposite arm.
- Do not cross the arms and do not support the raised elbow with the other hand.
- Keep shoulders relaxed and torso nearly front-facing.
- Shift only the eye direction slightly downward and to the side, while keeping the head angle almost unchanged.
- Mouth remains fully closed with a calm neutral-friendly expression; no frown, confusion, surprise, authority, or exaggerated “thinking” acting.

Absolute identity and appearance invariants:
Preserve the exact same approximately 35-year-old East Asian woman: same facial identity and geometry, apparent age, skin tone, eyes, eyebrows, nose, lips, jawline, ears, tied-back dark hairstyle, ponytail silhouette, hairline, loose strands, makeup level, and professional temperament. Preserve the refined semi-realistic 3D rendering, skin texture, fabric realism, edge quality, and studio light direction.

Uniform invariants:
Preserve the exact deep navy formal highway public-service uniform, slate-blue collared shirt, dark navy tie, notch/service collar, straight tailoring, pockets, seams, trousers, and all plain gold metal buttons. Keep the same button count, size, tone, and unmarked surfaces. Do not add or remove shoulder devices or any accessory.

Composition invariants:
Preserve the 1024×1536 vertical 2:3 canvas, same head size, face size, head-top position, shoulder line, person-to-canvas scale, centered placement, three-quarter-body crop, bottom canvas contact, camera distance, perspective, and lighting. The change in arm silhouette is allowed, but do not zoom out, show feet, or crop the head or hands.

Scene/backdrop:
Genuinely transparent background with a clean isolated character cutout.

Strict avoid:
No changed face, changed age, changed hairstyle, changed clothing design, changed button layout, open mouth, extra fingers, fused fingers, missing fingers, broken wrist, duplicated arm, arm crossing, hand covering the mouth, fist, pointing gesture, prop, tablet, glasses, cap, badge, emblem, rank, text, logo, watermark, background scene, halo, floor, shadow plane, checkerboard, white/black backdrop, or pseudo-transparency.

Output:
One native 1024×1536 RGBA PNG with fully transparent corners, narrow clean antialiasing, and no baked checkerboard.
```

## Thinking：袖口清理提示词

```text
Use case: precise-object-edit
Asset type: Figure A thinking-pose master cleanup
Input images: Image 1 is the ONLY edit target.

Primary request:
Remove ONLY the extra gold sleeve/cuff buttons that appeared on both sleeves in Image 1.

Precise edit:
- Remove the three small gold buttons arranged vertically on the raised right sleeve cuff (viewer’s left).
- Remove the two small gold buttons near the lowered left sleeve cuff (viewer’s right).
- Reconstruct each removed area with seamless matching deep navy sleeve fabric, preserving the exact local weave, folds, seams, highlights, and shadows.
- Keep exactly the three existing plain gold buttons on the center front of the jacket. Preserve those three front buttons exactly in count, position, size, tone, and highlight.

Pixel-lock everything else:
Preserve the exact woman, face, age, skin, hair, ponytail, downward side gaze, closed mouth, right-hand-at-chin thinking gesture, left hand at lower abdomen, hands, fingers, arms, body, pose, camera, scale, crop, lighting, jacket, shirt, tie, front buttons, pockets, trousers, semi-realistic 3D rendering, and transparent cutout silhouette. Do not redraw or reposition anything outside the five tiny cuff-button regions.

Strict avoid:
No new button, badge, emblem, insignia, rank, shoulder mark, text, logo, watermark, altered hand, altered finger, changed face, changed pose, changed crop, background, halo, checkerboard, white/black backdrop, or other edit.

Output:
One 1024×1536 RGBA PNG with genuine transparent background and fully transparent corners.
```

## Explaining：身份保持编辑提示词

```text
Use case: identity-preserve
Asset type: Figure A “explaining” pose master for a lightweight Vue digital-human state prototype
Input images: Image 1 is the ONLY edit target and the absolute identity, clothing, style, camera, lighting, scale, crop, and transparency anchor.

Primary request:
Change ONLY the body pose, hands, and arms of the woman in Image 1 to a restrained professional explanation / briefing pose. Keep the same person, same face, same closed-mouth expression, and same uniform.

Required explaining pose:
- Raise the subject’s right hand (viewer’s left) to the space between waist and lower chest in a small open explanatory gesture.
- The raised palm faces gently upward and slightly toward the viewer, with fingers together but naturally relaxed and anatomically correct.
- Keep the raised elbow close to the torso; the gesture is compact and collaborative, not theatrical.
- Place the subject’s left hand naturally at the lower waist/abdomen, relaxed and clearly visible.
- Keep shoulders relaxed, torso nearly front-facing, head angle and direct friendly gaze unchanged.
- Mouth remains fully closed with the same restrained friendly smile. No mouth movement or speaking mouth shape.

Absolute identity and appearance invariants:
Preserve the exact same approximately 35-year-old East Asian woman: same facial identity and geometry, apparent age, skin tone, eye shape and gaze, eyebrows, nose, closed lips, jawline, ears, tied-back dark hairstyle, ponytail silhouette, hairline, loose strands, makeup level, and professional temperament. Preserve the refined semi-realistic 3D rendering, skin texture, fabric realism, edge quality, and studio light direction.

Uniform invariants:
Preserve the exact deep navy formal highway public-service uniform, slate-blue collared shirt, dark navy tie, notch/service collar, straight tailoring, pockets, seams, trousers, and exactly the same three visible plain gold metal buttons on the center front. Keep their count, position, size, tone, and unmarked surfaces. Do not create any gold buttons on sleeves or cuffs. Do not add shoulder devices or accessories.

Composition invariants:
Preserve the 1024×1536 vertical 2:3 canvas, same head size, face size, head-top position, shoulder line, person-to-canvas scale, centered placement, three-quarter-body crop, bottom canvas contact, camera distance, perspective, and lighting. The change in arm silhouette is allowed, but do not zoom out, show feet, or crop the head or hands.

Scene/backdrop:
Genuinely transparent background with a clean isolated character cutout.

Strict avoid:
No changed face, changed age, changed hairstyle, changed clothing design, changed front-button layout, cuff buttons, sleeve buttons, open mouth, teeth, extra fingers, fused fingers, missing fingers, broken wrist, duplicated arm, pointing, fist, raised index finger, command gesture, broad hosting gesture, prop, tablet, glasses, cap, badge, emblem, rank, text, logo, watermark, background scene, halo, floor, shadow plane, checkerboard, white/black backdrop, or pseudo-transparency.

Output:
One native 1024×1536 RGBA PNG with fully transparent corners, narrow clean antialiasing, and no baked checkerboard.
```

## Explaining：袖口清理提示词

```text
Use case: precise-object-edit
Asset type: Figure A explaining-pose master cleanup
Input images: Image 1 is the ONLY edit target.

Primary request:
Remove ONLY the two tiny dark-and-gold cuff-button details visible below the open raised palm on the subject’s right sleeve (viewer’s left). Reconstruct those two tiny regions with seamless matching deep navy sleeve fabric, retaining the exact cuff edge, weave, folds, highlights, and shadows.

Keep exactly the three existing plain gold buttons on the center front of the jacket. Preserve those three front buttons exactly in count, position, size, tone, and highlight. Do not alter any other region.

Pixel-lock everything else:
Preserve the exact woman, face, closed-mouth smile, direct gaze, hair, ponytail, open-palm explaining gesture, other hand at lower abdomen, all fingers, arms, body, pose, camera, scale, crop, lighting, jacket, shirt, tie, front buttons, pockets, trousers, semi-realistic 3D rendering, and transparent cutout silhouette.

Strict avoid:
No new button, badge, emblem, insignia, rank, text, logo, watermark, altered hand, altered finger, changed face, changed pose, changed crop, background, halo, checkerboard, white/black backdrop, or any other edit.

Output:
One 1024×1536 RGBA PNG with genuine transparent background and fully transparent corners.
```
