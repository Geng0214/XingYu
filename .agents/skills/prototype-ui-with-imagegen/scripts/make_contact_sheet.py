#!/usr/bin/env python3
"""Compose labeled UI prototype images into one comparison sheet."""

from __future__ import annotations

import argparse
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont, ImageOps


BACKGROUND = "#F2F2F7"
CARD = "#FFFFFF"
TEXT = "#111111"
MUTED = "#6E6E73"


def parse_item(value: str) -> tuple[str, Path]:
    if "=" not in value:
        raise argparse.ArgumentTypeError("--item must use LABEL=/absolute/path")
    label, raw_path = value.split("=", 1)
    path = Path(raw_path).expanduser()
    if not label.strip():
        raise argparse.ArgumentTypeError("item label cannot be empty")
    if not path.is_absolute():
        raise argparse.ArgumentTypeError("item path must be absolute")
    if not path.is_file():
        raise argparse.ArgumentTypeError(f"image does not exist: {path}")
    return label.strip(), path


def font(size: int, bold: bool = False) -> ImageFont.ImageFont:
    candidates = [
        "/System/Library/Fonts/SFNS.ttf" if not bold else "/System/Library/Fonts/SFNSBold.ttf",
        "/System/Library/Fonts/Helvetica.ttc",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
        if bold
        else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ]
    for candidate in candidates:
        try:
            return ImageFont.truetype(candidate, size=size)
        except OSError:
            continue
    return ImageFont.load_default()


def fit_image(image: Image.Image, width: int, height: int) -> Image.Image:
    contained = ImageOps.contain(ImageOps.exif_transpose(image).convert("RGB"), (width, height))
    canvas = Image.new("RGB", (width, height), CARD)
    x = (width - contained.width) // 2
    y = (height - contained.height) // 2
    canvas.paste(contained, (x, y))
    return canvas


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--item", action="append", required=True, type=parse_item)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--columns", type=int, default=0, help="0 chooses a balanced layout")
    parser.add_argument("--cell-width", type=int, default=720)
    parser.add_argument("--cell-height", type=int, default=1280)
    parser.add_argument("--title", default="UI Prototype Directions")
    args = parser.parse_args()

    if not 2 <= len(args.item) <= 5:
        parser.error("provide between 2 and 5 --item values")
    if args.columns < 0:
        parser.error("--columns cannot be negative")

    count = len(args.item)
    columns = args.columns or (count if count <= 3 else math.ceil(count / 2))
    columns = min(columns, count)
    rows = math.ceil(count / columns)

    outer = 56
    gap = 32
    title_height = 116
    label_height = 86
    card_pad = 18
    card_width = args.cell_width + card_pad * 2
    card_height = args.cell_height + label_height + card_pad * 2
    sheet_width = outer * 2 + columns * card_width + (columns - 1) * gap
    sheet_height = outer * 2 + title_height + rows * card_height + (rows - 1) * gap

    sheet = Image.new("RGB", (sheet_width, sheet_height), BACKGROUND)
    draw = ImageDraw.Draw(sheet)
    draw.text((outer, outer), args.title, fill=TEXT, font=font(44, bold=True))
    draw.text(
        (outer, outer + 58),
        "Full-resolution directions remain the source of truth.",
        fill=MUTED,
        font=font(23),
    )

    for index, (label, path) in enumerate(args.item):
        row, column = divmod(index, columns)
        x = outer + column * (card_width + gap)
        y = outer + title_height + row * (card_height + gap)
        draw.rounded_rectangle(
            (x, y, x + card_width, y + card_height),
            radius=28,
            fill=CARD,
        )
        draw.text(
            (x + card_pad + 8, y + card_pad + 12),
            label,
            fill=TEXT,
            font=font(30, bold=True),
        )
        with Image.open(path) as source:
            fitted = fit_image(source, args.cell_width, args.cell_height)
        sheet.paste(fitted, (x + card_pad, y + card_pad + label_height))

    args.output = args.output.expanduser().absolute()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(args.output, format="PNG", optimize=True)
    print(args.output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
