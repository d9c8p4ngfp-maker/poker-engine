"""
Generate multiple pixel-art variants of an image for comparison.
Usage: python pixelize_test.py <input_image>
"""
from PIL import Image
import os
import sys
from pathlib import Path

OUTPUT_DIR = Path(__file__).resolve().parent.parent / "web" / "public" / "pixel-tests"

# Card aspect ratio: ~2.5 : 3.5, e.g. 250x350
CARD_W, CARD_H = 250, 350

# Different pixel "resolutions" (how many pixels wide the card is)
PIXEL_SIZES = [16, 24, 32, 48, 64]


def pixelize(img: Image.Image, pixel_w: int, pixel_h: int) -> Image.Image:
    """
    Convert to pixel art:
    1. Downscale to a tiny grid (pixel_w × pixel_h) with LANCZOS
    2. Quantize to reduced colors (optional, for retro palette feel)
    3. Upscale back to target card size with NEAREST (keeps sharp pixels)
    """
    # Step 1: Downscale
    tiny = img.resize((pixel_w, pixel_h), Image.LANCZOS)

    # Step 2: Optional color quantization (reduce to ~64 colors for retro feel)
    tiny = tiny.quantize(colors=64, method=Image.Quantize.MEDIANCUT).convert("RGB")

    # Step 3: Upscale with NEAREST to get crisp pixels
    result = tiny.resize((CARD_W, CARD_H), Image.NEAREST)
    return result


def main():
    if len(sys.argv) < 2:
        print(f"Usage: python {sys.argv[0]} <image_path>")
        sys.exit(1)

    src = Path(sys.argv[1])
    if not src.exists():
        print(f"File not found: {src}")
        sys.exit(1)

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    # Open and crop to card ratio (center crop)
    img = Image.open(src).convert("RGB")
    orig_w, orig_h = img.size

    # Cropping to 5:7 ratio (card proportions)
    target_ratio = CARD_W / CARD_H
    current_ratio = orig_w / orig_h

    if current_ratio > target_ratio:
        # Image is wider → crop width
        new_w = int(orig_h * target_ratio)
        left = (orig_w - new_w) // 2
        img = img.crop((left, 0, left + new_w, orig_h))
    else:
        # Image is taller → crop height
        new_h = int(orig_w / target_ratio)
        top = (orig_h - new_h) // 2
        img = img.crop((0, top, orig_w, top + new_h))

    print(f"Original: {orig_w}x{orig_h}")
    print(f"Cropped to card ratio: {img.size[0]}x{img.size[1]}")
    print()

    results = []

    # Generate each variant
    for pw in PIXEL_SIZES:
        ph = int(pw / CARD_W * CARD_H)
        result = pixelize(img, pw, ph)
        fname = f"pixel_{pw}x{ph}.png"
        outpath = OUTPUT_DIR / fname
        result.save(outpath)
        print(f"  {pw:>3}x{ph:<3} → {fname}  ({outpath.stat().st_size:,} bytes)")
        results.append((pw, ph, fname))

    # Also save cropped original for reference
    ref_path = OUTPUT_DIR / "original_cropped.png"
    img_ref = img.resize((CARD_W, CARD_H), Image.LANCZOS)
    img_ref.save(ref_path)
    print(f"  ref   → original_cropped.png  ({ref_path.stat().st_size:,} bytes)")

    # Generate HTML comparison page
    html = html_comparison(results)
    html_path = OUTPUT_DIR / "compare.html"
    html_path.write_text(html, encoding="utf-8")
    print(f"\nComparison page: {html_path}")
    print(f"Open: http://localhost:5173/pixel-tests/compare.html")


def html_comparison(results):
    cards = "".join(
        f"""<div class="variant">
  <img src="{fname}" alt="{pw}x{ph}">
  <span class="label">{pw}×{ph} pixels</span>
</div>"""
        for pw, ph, fname in results
    )

    return f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Pixel Art Comparison</title>
<style>
  @import url('https://fonts.googleapis.com/css2?family=Press+Start+2P&display=swap');
  * {{ margin: 0; padding: 0; box-sizing: border-box; }}
  body {{
    background: #0a0a0a;
    color: #ccc;
    font-family: 'Press Start 2P', monospace;
    min-height: 100vh;
    padding: 30px 20px;
  }}
  h1 {{
    text-align: center;
    font-size: 14px;
    color: #c8a860;
    margin-bottom: 8px;
    text-shadow: 2px 2px 0 #000;
  }}
  .subtitle {{
    text-align: center;
    font-size: 7px;
    color: #665a40;
    margin-bottom: 40px;
  }}
  .grid {{
    display: flex;
    flex-wrap: wrap;
    gap: 24px;
    justify-content: center;
    max-width: 1200px;
    margin: 0 auto;
  }}
  .variant {{
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 10px;
  }}
  .variant img {{
    width: 200px;
    height: 280px;
    object-fit: contain;
    image-rendering: pixelated;
    image-rendering: crisp-edges;
    border: 2px solid #332010;
    border-radius: 4px;
    background: #111;
  }}
  .variant img:hover {{
    border-color: #c8a860;
    transform: scale(1.08);
    transition: 0.15s;
  }}
  .label {{
    font-size: 8px;
    color: #c8a860;
  }}
  .best-hint {{
    text-align: center;
    margin-top: 40px;
    font-size: 8px;
    color: #886a30;
    line-height: 2;
  }}
</style>
</head>
<body>
<h1>PIXEL ART CONVERSION TEST</h1>
<p class="subtitle">Same image · different pixel grid sizes</p>
<div class="grid">
  <div class="variant">
    <img src="original_cropped.png" alt="original">
    <span class="label">Original</span>
  </div>
  {cards}
</div>
<p class="best-hint">
  Smaller number = chunkier pixels (more retro)<br>
  Larger number = more detail preserved<br>
  All cards rendered at same size (250×350)
</p>
</body>
</html>"""


if __name__ == "__main__":
    main()
