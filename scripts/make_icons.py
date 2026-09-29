"""Generate PWA icons: gradient rounded square with three white flashcard shapes."""

from pathlib import Path

from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parent.parent / "static" / "icons"
OUT.mkdir(parents=True, exist_ok=True)

GRAD_TOP = (79, 70, 229)    # indigo-600
GRAD_BOTTOM = (6, 182, 212)  # cyan-500
WHITE = (255, 255, 255)


def rounded_card(draw: ImageDraw.ImageDraw, box, radius: int, fill=WHITE, width: int = 0):
    x0, y0, x1, y1 = box
    draw.rounded_rectangle([x0, y0, x1, y1], radius=radius, fill=fill, width=width)


def make_icon(size: int, rounded: bool = True) -> Image.Image:
    img = Image.new("RGBA", (size, size))
    d = ImageDraw.Draw(img)

    # vertical gradient background
    for y in range(size):
        t = y / (size - 1)
        r = round(GRAD_TOP[0] + (GRAD_BOTTOM[0] - GRAD_TOP[0]) * t)
        g = round(GRAD_TOP[1] + (GRAD_BOTTOM[1] - GRAD_TOP[1]) * t)
        b = round(GRAD_TOP[2] + (GRAD_BOTTOM[2] - GRAD_TOP[2]) * t)
        d.line([(0, y), (size, y)], fill=(r, g, b, 255))

    # three fanned flashcard shapes
    s = size / 512
    cards = [
        (70, 128, 300, 358, -13),
        (110, 100, 340, 330, -2),
        (150, 140, 380, 370, 11),
    ]
    for x0, y0, x1, y1, angle in cards:
        layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        ld = ImageDraw.Draw(layer)
        rounded_card(ld, [x0 * s, y0 * s, x1 * s, y1 * s], radius=int(28 * s), fill=(255, 255, 255, 235))
        layer = layer.rotate(angle, resample=Image.BICUBIC, center=(size / 2, size / 2))
        img = Image.alpha_composite(img, layer)

    if rounded:
        mask = Image.new("L", (size, size), 0)
        md = ImageDraw.Draw(mask)
        md.rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * 0.22), fill=255)
        img.putalpha(mask)

    return img


def main() -> None:
    make_icon(512, rounded=False).save(OUT / "icon-512.png")
    make_icon(192).save(OUT / "icon-192.png")
    make_icon(180).save(OUT / "apple-touch-icon.png")
    make_icon(64).save(OUT / "favicon-64.png")
    ico = make_icon(64, rounded=False)
    (OUT.parent / "favicon.ico").save(ico, sizes=[(16, 16), (32, 32), (48, 48), (64, 64)])
    print("icons written to", OUT)


if __name__ == "__main__":
    main()
