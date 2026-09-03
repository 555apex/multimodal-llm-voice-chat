#!/usr/bin/env python3
"""Prepare transparent Road Agent digital-human assets for the web frontend."""

from __future__ import annotations

import argparse
import hashlib
import json
from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image


CANVAS = (1024, 1536)
POSES = ("idle", "thinking", "explaining")
TARGET_TOP = 16
TARGET_BASELINE = 1518


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def remove_connected_checkerboard(image: Image.Image) -> Image.Image:
    """Remove the pale connected checkerboard emitted by the image generator.

    Only pale, near-neutral pixels connected to the canvas edge are removed.
    Enclosed white uniform details and eye highlights therefore remain intact.
    """

    rgb = np.asarray(image.convert("RGB"), dtype=np.uint8)
    high = rgb.max(axis=2)
    low = rgb.min(axis=2)
    candidate = (low >= 225) & ((high - low) <= 8)
    height, width = candidate.shape
    background = np.zeros((height, width), dtype=bool)
    queue: deque[tuple[int, int]] = deque()

    def add(y: int, x: int) -> None:
        if candidate[y, x] and not background[y, x]:
            background[y, x] = True
            queue.append((y, x))

    for x in range(width):
        add(0, x)
        add(height - 1, x)
    for y in range(height):
        add(y, 0)
        add(y, width - 1)

    while queue:
        y, x = queue.popleft()
        if y > 0:
            add(y - 1, x)
        if y + 1 < height:
            add(y + 1, x)
        if x > 0:
            add(y, x - 1)
        if x + 1 < width:
            add(y, x + 1)

    alpha = np.where(background, 0, 255).astype(np.uint8)
    rgba = np.dstack((rgb, alpha))
    rgba[alpha == 0, :3] = 0
    return Image.fromarray(rgba, mode="RGBA")


def semantic_remove_background(image: Image.Image, session: object) -> Image.Image:
    from rembg import remove

    strict = remove_connected_checkerboard(image)
    semantic = remove(
        image.convert("RGB"),
        session=session,
        alpha_matting=False,
    ).convert("RGBA")
    rgba = np.asarray(image.convert("RGBA"), dtype=np.uint8).copy()
    rgba[:, :, 3] = np.asarray(strict.getchannel("A"), dtype=np.uint8)
    semantic_alpha = np.asarray(semantic.getchannel("A"), dtype=np.uint8)
    shoe_top = round(image.height * 0.79)
    rgba[shoe_top:, :, 3] = np.maximum(rgba[shoe_top:, :, 3], semantic_alpha[shoe_top:, :])
    rgba[rgba[:, :, 3] == 0, :3] = 0
    return Image.fromarray(rgba, mode="RGBA")


def prepare_rgba(path: Path, remove_checkerboard: bool, session: object | None) -> Image.Image:
    image = Image.open(path)
    if image.size != CANVAS:
        raise ValueError(f"{path} has size {image.size}; expected {CANVAS}")
    if remove_checkerboard:
        image = semantic_remove_background(image, session) if session else remove_connected_checkerboard(image)
    else:
        image = image.convert("RGBA")
    if image.getextrema()[3] == (255, 255):
        raise ValueError(f"{path} is still fully opaque after processing")
    return normalize_canvas(image)


def normalize_canvas(image: Image.Image) -> Image.Image:
    """Align each pose to one foot baseline while retaining the 2:3 canvas."""

    alpha = np.asarray(image.getchannel("A"), dtype=np.uint8)
    visible = np.argwhere(alpha >= 32)
    if not visible.size:
        raise ValueError("asset contains no visible subject")
    y0, x0 = visible.min(axis=0)
    y1, x1 = visible.max(axis=0)
    subject = image.crop((int(x0), int(y0), int(x1) + 1, int(y1) + 1))
    target_height = TARGET_BASELINE - TARGET_TOP + 1
    scale = target_height / subject.height
    target_width = round(subject.width * scale)
    if target_width > CANVAS[0]:
        scale = CANVAS[0] / subject.width
        target_width = CANVAS[0]
        target_height = round(subject.height * scale)
    subject = subject.resize((target_width, target_height), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", CANVAS, (0, 0, 0, 0))
    x = (CANVAS[0] - target_width) // 2
    y = TARGET_BASELINE - target_height + 1
    canvas.alpha_composite(subject, (x, y))
    return canvas


def save_pose(image: Image.Image, pose: str, archive_dir: Path, public_dir: Path) -> list[Path]:
    archive_path = archive_dir / f"guardian-{pose}-master.png"
    png_path = public_dir / f"guardian-{pose}.png"
    webp_path = public_dir / f"guardian-{pose}.webp"
    image.save(archive_path, format="PNG", optimize=True)
    image.save(png_path, format="PNG", optimize=True)
    image.save(webp_path, format="WEBP", lossless=True, method=6)
    return [archive_path, png_path, webp_path]


def alpha_stats(path: Path) -> dict[str, int | bool]:
    image = Image.open(path).convert("RGBA")
    alpha = np.asarray(image.getchannel("A"), dtype=np.uint8)
    nonzero = np.argwhere(alpha > 0)
    if not nonzero.size:
        raise ValueError(f"{path} contains no visible pixels")
    y0, x0 = nonzero.min(axis=0)
    y1, x1 = nonzero.max(axis=0)
    return {
        "hasAlpha": bool(alpha.min() < 255),
        "transparentPixels": int((alpha == 0).sum()),
        "partialAlphaPixels": int(((alpha > 0) & (alpha < 255)).sum()),
        "boundsX": int(x0),
        "boundsY": int(y0),
        "boundsWidth": int(x1 - x0 + 1),
        "boundsHeight": int(y1 - y0 + 1),
        "footBaselineY": int(y1),
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    for pose in POSES:
        parser.add_argument(f"--{pose}-source", type=Path, required=True)
    parser.add_argument("--archive-dir", type=Path, required=True)
    parser.add_argument("--public-dir", type=Path, required=True)
    parser.add_argument("--segmentation-model", default="")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    args.archive_dir.mkdir(parents=True, exist_ok=True)
    args.public_dir.mkdir(parents=True, exist_ok=True)
    sources = {pose: getattr(args, f"{pose}_source").resolve() for pose in POSES}
    session = None
    if args.segmentation_model:
        from rembg import new_session

        session = new_session(args.segmentation_model)
    outputs: list[Path] = []
    for pose, source in sources.items():
        outputs.extend(save_pose(
            prepare_rgba(source, remove_checkerboard=pose != "explaining", session=session),
            pose,
            args.archive_dir,
            args.public_dir,
        ))

    manifest = {
        "canvas": {"width": CANVAS[0], "height": CANVAS[1]},
        "preparation": {
            "segmentationModel": args.segmentation_model or "connected-checkerboard-mask",
            "targetTopY": TARGET_TOP,
            "targetFootBaselineY": TARGET_BASELINE,
        },
        "sources": {pose: {"file": path.name, "sha256": sha256(path)} for pose, path in sources.items()},
        "assets": {},
    }
    for path in outputs:
        manifest["assets"][path.name] = {
            "sha256": sha256(path),
            "bytes": path.stat().st_size,
            "mode": Image.open(path).mode,
            "size": list(Image.open(path).size),
            **alpha_stats(path),
        }
    manifest_path = args.archive_dir / "ASSET_MANIFEST.json"
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(manifest_path)


if __name__ == "__main__":
    main()
