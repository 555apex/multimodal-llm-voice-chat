#!/usr/bin/env python3
"""Create pixel-stable mouth frames for the Road Agent digital human."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter


CANVAS = (1024, 1536)
MOUTH_ROI = (374, 638, 528, 760)
FEATHER_PIXELS = 14
SHAPES = ("closed", "half", "open")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_canvas(path: Path, *, allow_generated_height: bool = False) -> Image.Image:
    image = Image.open(path).convert("RGBA")
    if allow_generated_height and image.size == (CANVAS[0], CANVAS[1] - 1):
        canvas = Image.new("RGBA", CANVAS, (0, 0, 0, 0))
        canvas.alpha_composite(image, (0, 0))
        return canvas
    if image.size != CANVAS:
        raise ValueError(f"{path} has size {image.size}; expected {CANVAS}")
    return image


def likely_mouth(image: Image.Image) -> np.ndarray:
    rgb = np.asarray(image.convert("RGB"), dtype=np.int16)
    red, green, blue = rgb[:, :, 0], rgb[:, :, 1], rgb[:, :, 2]
    chromatic_red = (red - green > 34) & (red - blue > 20) & (green < 180)
    dark_interior = (red + green + blue < 390) & (green < 145)
    return chromatic_red | dark_interior


def expanded_mask(selected: np.ndarray, size: int, blur: int) -> np.ndarray:
    mask = Image.fromarray(np.where(selected, 255, 0).astype(np.uint8), mode="L")
    mask = mask.filter(ImageFilter.MaxFilter(size))
    mask = mask.filter(ImageFilter.GaussianBlur(blur))
    return np.asarray(mask, dtype=np.float32) / 255.0


def roi_edge_weight(shape: tuple[int, int]) -> np.ndarray:
    height, width = shape
    y, x = np.ogrid[:height, :width]
    distance = np.minimum(
        np.minimum(x + 1, width - x),
        np.minimum(y + 1, height - y),
    ).astype(np.float32)
    weight = np.clip(distance / FEATHER_PIXELS, 0.0, 1.0)
    return weight * weight * (3.0 - 2.0 * weight)


def inpaint_skin(image: Image.Image, selected: np.ndarray) -> np.ndarray:
    """Fill the old mouth with a smooth continuation of surrounding face skin."""

    rgb = np.asarray(image.convert("RGB"), dtype=np.float32)
    work = rgb.copy()
    fill = np.median(rgb[~selected], axis=0)
    work[selected] = fill
    for _ in range(500):
        padded = np.pad(work, ((1, 1), (1, 1), (0, 0)), mode="edge")
        neighbours = (
            padded[:-2, 1:-1]
            + padded[2:, 1:-1]
            + padded[1:-1, :-2]
            + padded[1:-1, 2:]
        ) * 0.25
        change = float(np.max(np.abs(work[selected] - neighbours[selected])))
        work[selected] = neighbours[selected]
        if change < 0.02:
            break
    return work


def match_local_skin(generated: Image.Image, canonical: Image.Image) -> Image.Image:
    """Match low-frequency skin colour while retaining generated lip detail."""

    generated_rgb = np.asarray(generated.convert("RGB"), dtype=np.float32)
    canonical_rgb = np.asarray(canonical.convert("RGB"), dtype=np.float32)
    generated_smooth = np.asarray(
        generated.convert("RGB").filter(ImageFilter.GaussianBlur(28)),
        dtype=np.float32,
    )
    canonical_smooth = np.asarray(
        canonical.convert("RGB").filter(ImageFilter.GaussianBlur(28)),
        dtype=np.float32,
    )
    corrected = np.clip(
        generated_rgb + canonical_smooth - generated_smooth,
        0,
        255,
    ).astype(np.uint8)
    return Image.fromarray(corrected, mode="RGB").convert("RGBA")


def composite_mouth(canonical: Image.Image, generated: Image.Image) -> Image.Image:
    canonical_pixels = np.asarray(canonical, dtype=np.uint8)
    result = canonical.copy()
    base_crop = canonical.crop(MOUTH_ROI)
    generated_crop = match_local_skin(generated.crop(MOUTH_ROI), base_crop)

    canonical_rgb = np.asarray(base_crop.convert("RGB"), dtype=np.float32)
    edge_weight = roi_edge_weight(canonical_rgb.shape[:2])
    old_core = expanded_mask(likely_mouth(base_crop), 25, 0) >= 0.5
    old_feather = (
        expanded_mask(likely_mouth(base_crop), 25, 7) * edge_weight
    )[:, :, None]
    skin = inpaint_skin(base_crop, old_core)
    neutral_rgb = canonical_rgb * (1.0 - old_feather) + skin * old_feather

    generated_rgb = np.asarray(generated_crop.convert("RGB"), dtype=np.float32)
    new_feather = (
        expanded_mask(likely_mouth(generated_crop), 29, FEATHER_PIXELS)
        * edge_weight
    )[:, :, None]
    patch_rgb = neutral_rgb * (1.0 - new_feather) + generated_rgb * new_feather
    patch = Image.fromarray(np.clip(patch_rgb, 0, 255).astype(np.uint8), mode="RGB").convert("RGBA")
    patch.putalpha(base_crop.getchannel("A"))
    result.paste(patch, MOUTH_ROI[:2])

    result_pixels = np.asarray(result, dtype=np.uint8)
    left, top, right, bottom = MOUTH_ROI
    outside = np.ones((CANVAS[1], CANVAS[0]), dtype=bool)
    outside[top:bottom, left:right] = False
    if not np.array_equal(result_pixels[outside], canonical_pixels[outside]):
        raise ValueError("pixels outside the mouth ROI changed")
    if not np.array_equal(result_pixels[:, :, 3], canonical_pixels[:, :, 3]):
        raise ValueError("the canonical alpha channel changed")
    if int(np.count_nonzero(result_pixels != canonical_pixels)) < 100:
        raise ValueError("generated mouth frame did not materially differ")
    return result


def alpha_stats(image: Image.Image) -> dict[str, int | bool | list[int]]:
    alpha = np.asarray(image.getchannel("A"), dtype=np.uint8)
    return {
        "mode": image.mode,
        "size": list(image.size),
        "hasAlpha": bool(alpha.min() < 255),
        "transparentPixels": int((alpha == 0).sum()),
        "partialAlphaPixels": int(((alpha > 0) & (alpha < 255)).sum()),
    }


def save_frame(
    image: Image.Image,
    shape: str,
    archive_dir: Path,
    public_dir: Path,
) -> list[Path]:
    stem = f"guardian-explaining-mouth-{shape}-v2"
    archive = archive_dir / f"{stem}-master.png"
    png = public_dir / f"{stem}.png"
    webp = public_dir / f"{stem}.webp"
    image.save(archive, format="PNG", optimize=True)
    image.save(png, format="PNG", optimize=True)
    image.save(webp, format="WEBP", lossless=True, method=6)
    return [archive, png, webp]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--canonical", type=Path, required=True)
    parser.add_argument("--closed-source", type=Path, required=True)
    parser.add_argument("--half-source", type=Path, required=True)
    parser.add_argument("--archive-dir", type=Path, required=True)
    parser.add_argument("--public-dir", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    canonical_path = args.canonical.resolve()
    source_paths = {
        "closed": args.closed_source.resolve(),
        "half": args.half_source.resolve(),
    }
    canonical = load_canvas(canonical_path)
    generated = {
        shape: load_canvas(path, allow_generated_height=True)
        for shape, path in source_paths.items()
    }
    frames = {
        "closed": composite_mouth(canonical, generated["closed"]),
        "half": composite_mouth(canonical, generated["half"]),
        "open": canonical.copy(),
    }

    args.archive_dir.mkdir(parents=True, exist_ok=True)
    args.public_dir.mkdir(parents=True, exist_ok=True)
    outputs: dict[str, list[Path]] = {}
    for shape in SHAPES:
        outputs[shape] = save_frame(frames[shape], shape, args.archive_dir, args.public_dir)

    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    manifest["mouthAnimation"] = {
        "canonical": {
            "file": canonical_path.name,
            "sha256": sha256(canonical_path),
        },
        "generatedSources": {
            shape: {"file": path.name, "sha256": sha256(path)}
            for shape, path in source_paths.items()
        },
        "roi": {
            "left": MOUTH_ROI[0],
            "top": MOUTH_ROI[1],
            "right": MOUTH_ROI[2],
            "bottom": MOUTH_ROI[3],
            "featherPixels": FEATHER_PIXELS,
        },
        "invariants": {
            "outsideRoiPixelIdentical": True,
            "canonicalAlphaPreserved": True,
        },
        "assets": {},
    }
    for shape, paths in outputs.items():
        for path in paths:
            manifest["mouthAnimation"]["assets"][path.name] = {
                "shape": shape,
                "sha256": sha256(path),
                "bytes": path.stat().st_size,
                **alpha_stats(Image.open(path).convert("RGBA")),
            }
    args.manifest.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(args.manifest)


if __name__ == "__main__":
    main()
