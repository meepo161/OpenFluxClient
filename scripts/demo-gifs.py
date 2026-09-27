#!/usr/bin/env python3
"""Turns the DemoRecorder frames into the README's GIFs.

    OPENFLUX_DEMO=<dir> ./gradlew :desktopApp:test --tests '*DemoRecorder*'
    python scripts/demo-gifs.py <dir> docs/media

Desktop scenes become a Windows window on the app's blue with a pointer,
clicks and a gentle zoom towards what is clicked; Android scenes a phone
with touches. Needs Pillow.
"""
import json
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

FRAME_MS = 80
BLUE_TOP = (79, 124, 255)
BLUE_BOTTOM = (38, 60, 128)


def gradient(size, top=BLUE_TOP, bottom=BLUE_BOTTOM):
    w, h = size
    column = Image.new("RGB", (1, h))
    for y in range(h):
        t = y / max(1, h - 1)
        column.putpixel((0, y), tuple(round(a + (b - a) * t) for a, b in zip(top, bottom)))
    return column.resize((w, h))


def rounded_mask(size, radius):
    mask = Image.new("L", size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius, fill=255)
    return mask


def shadow(size, radius, blur=28, opacity=110):
    pad = blur * 2
    s = Image.new("RGBA", (size[0] + pad * 2, size[1] + pad * 2), (0, 0, 0, 0))
    ImageDraw.Draw(s).rounded_rectangle((pad, pad + 10, pad + size[0], pad + size[1] + 10), radius, fill=(0, 0, 0, opacity))
    return s.filter(ImageFilter.GaussianBlur(blur)), pad


def font(size, bold=False):
    for name in (["segoeuib.ttf", "arialbd.ttf"] if bold else ["segoeui.ttf", "arial.ttf"]):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


ICON = None


def icon(size):
    global ICON
    if ICON is None:
        ICON = Image.open(Path(__file__).resolve().parent.parent / "desktopApp/icons/openflux.png").convert("RGBA")
    return ICON.resize((size, size), Image.LANCZOS)


def pointer(scale=1.0):
    """A white arrow with a dark outline, like the Windows pointer."""
    pts = [(0, 0), (0, 17), (4.5, 13), (7.5, 20), (10, 19), (7, 12), (13, 12)]
    s = 3
    img = Image.new("RGBA", (int(16 * s * scale) + 6, int(24 * s * scale) + 6), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    poly = [(3 + x * s * scale, 3 + y * s * scale) for x, y in pts]
    d.polygon(poly, fill=(255, 255, 255, 255), outline=(20, 20, 20, 255))
    d.line(poly + [poly[0]], fill=(20, 20, 20, 255), width=max(2, int(2 * scale)))
    return img.resize((img.width // s, img.height // s), Image.LANCZOS)


def window(frame, title="OpenFlux"):
    """The frame in a Windows 11 style window: title bar, controls, rounded corners."""
    w, h = frame.size
    bar = 34
    win = Image.new("RGB", (w, h + bar), (18, 20, 28))
    d = ImageDraw.Draw(win)
    win.paste(icon(18), (12, 8), icon(18))
    d.text((38, 8), title, fill=(220, 224, 235), font=font(13))
    for i, glyph in enumerate(["—", "▢", "✕"]):
        d.text((w - 46 * (3 - i) + 18, 7), glyph, fill=(200, 204, 215), font=font(13))
    win.paste(frame, (0, bar))
    return win, bar


def desktop_scene(src: Path, out: Path, canvas=(1280, 820), scale=0.9, zoom_in=1.18):
    meta = json.loads((src / "path.json").read_text())
    frames = sorted(src.glob("f*.png"))
    moves = meta["frames"]
    bg = gradient(canvas)
    arrow = pointer()
    zoom = 1.0
    cx = cy = None
    out_frames = []
    for i, path in enumerate(frames):
        raw = Image.open(path).convert("RGB")
        x, y, pressed = moves[min(i, len(moves) - 1)]
        # Zoom in a little around clicks, and out again when the pointer moves on.
        near = any(m[2] for m in moves[max(0, i - 6): i + 8])
        zoom += ((zoom_in if near else 1.0) - zoom) * 0.18
        win, bar = window(raw)
        d = ImageDraw.Draw(win)
        px, py = x, y + bar
        if pressed:
            d.ellipse((px - 18, py - 18, px + 18, py + 18), outline=(120, 160, 255), width=3)
        win.paste(arrow, (int(px) - 3, int(py) - 3), arrow)
        ww, wh = win.size
        w2, h2 = int(ww * scale), int(wh * scale)
        win = win.resize((w2, h2), Image.LANCZOS)
        radius = 12
        sh, pad = shadow((w2, h2), radius)
        frame = bg.copy().convert("RGBA")
        ox, oy = (canvas[0] - w2) // 2, (canvas[1] - h2) // 2
        frame.alpha_composite(sh, (ox - pad, oy - pad))
        frame.paste(win, (ox, oy), rounded_mask((w2, h2), radius))
        # The zoom follows the pointer, eased so it does not jump.
        tx, ty = ox + px * scale, oy + py * scale
        cx = tx if cx is None else cx + (tx - cx) * 0.2
        cy = ty if cy is None else cy + (ty - cy) * 0.2
        if zoom > 1.01:
            vw, vh = canvas[0] / zoom, canvas[1] / zoom
            left = min(max(cx - vw / 2, 0), canvas[0] - vw)
            top = min(max(cy - vh / 2, 0), canvas[1] - vh)
            frame = frame.crop((int(left), int(top), int(left + vw), int(top + vh))).resize(canvas, Image.LANCZOS)
        out_frames.append(frame.convert("RGB").resize((canvas[0] * 7 // 10, canvas[1] * 7 // 10), Image.LANCZOS))
    save_gif(out_frames, out)


def phone(frame, clock="12:30"):
    """The frame in a phone: status bar, bezel, rounded screen."""
    w, h = frame.size
    status = 30
    screen = Image.new("RGB", (w, h + status), (250, 250, 252))
    d = ImageDraw.Draw(screen)
    d.text((20, 7), clock, fill=(30, 30, 35), font=font(13, bold=True))
    # Battery, wifi and the VPN key on the right.
    d.rounded_rectangle((w - 44, 10, w - 20, 21), 3, outline=(30, 30, 35), width=2)
    d.rectangle((w - 42, 12, w - 26, 19), fill=(30, 30, 35))
    d.text((w - 76, 5), "◢", fill=(30, 30, 35), font=font(14))
    screen.paste(frame, (0, status))
    bezel = 14
    body = Image.new("RGBA", (w + bezel * 2, h + status + bezel * 2), (0, 0, 0, 0))
    ImageDraw.Draw(body).rounded_rectangle((0, 0, body.width - 1, body.height - 1), 46, fill=(17, 18, 22, 255))
    body.paste(screen, (bezel, bezel), rounded_mask(screen.size, 34))
    ImageDraw.Draw(body).ellipse((body.width // 2 - 6, bezel + 9, body.width // 2 + 6, bezel + 21), fill=(8, 8, 10, 255))
    return body, bezel, status


def android_scene(src: Path, out: Path, canvas=(520, 1000), scale=1.0):
    meta = json.loads((src / "path.json").read_text())
    frames = sorted(src.glob("f*.png"))
    moves = meta["frames"]
    bg = gradient(canvas)
    out_frames = []
    for i, path in enumerate(frames):
        raw = Image.open(path).convert("RGB")
        x, y, pressed = moves[min(i, len(moves) - 1)]
        body, bezel, status = phone(raw)
        if pressed:
            touch = Image.new("RGBA", body.size, (0, 0, 0, 0))
            tx, ty = x + bezel, y + bezel + status
            ImageDraw.Draw(touch).ellipse((tx - 22, ty - 22, tx + 22, ty + 22), fill=(90, 120, 255, 110), outline=(255, 255, 255, 200), width=2)
            body.alpha_composite(touch)
        w2, h2 = int(body.width * scale), int(body.height * scale)
        body = body.resize((w2, h2), Image.LANCZOS)
        sh, pad = shadow((w2, h2), 46, blur=24, opacity=120)
        frame = bg.copy().convert("RGBA")
        ox, oy = (canvas[0] - w2) // 2, (canvas[1] - h2) // 2
        frame.alpha_composite(sh, (ox - pad, oy - pad))
        frame.alpha_composite(body, (ox, oy))
        out_frames.append(frame.convert("RGB").resize((canvas[0] * 3 // 5, canvas[1] * 3 // 5), Image.LANCZOS))
    save_gif(out_frames, out)


def hero(desktop: Path, android: Path, out: Path, canvas=(1280, 760)):
    """The desktop window and the phone, both connecting."""
    d_meta = json.loads((desktop / "path.json").read_text())["frames"]
    a_meta = json.loads((android / "path.json").read_text())["frames"]
    d_frames = sorted(desktop.glob("f*.png"))
    a_frames = sorted(android.glob("f*.png"))
    n = max(len(d_frames), len(a_frames))
    bg = gradient(canvas)
    arrow = pointer()
    out_frames = []
    for i in range(n):
        frame = bg.copy().convert("RGBA")
        raw = Image.open(d_frames[min(i, len(d_frames) - 1)]).convert("RGB")
        x, y, pressed = d_meta[min(i, len(d_meta) - 1)]
        win, bar = window(raw)
        dr = ImageDraw.Draw(win)
        if pressed:
            dr.ellipse((x - 18, y + bar - 18, x + 18, y + bar + 18), outline=(120, 160, 255), width=3)
        win.paste(arrow, (int(x) - 3, int(y + bar) - 3), arrow)
        s = 0.86
        w2, h2 = int(win.width * s), int(win.height * s)
        win = win.resize((w2, h2), Image.LANCZOS)
        sh, pad = shadow((w2, h2), 12)
        frame.alpha_composite(sh, (40 - pad, 70 - pad))
        frame.paste(win, (40, 70), rounded_mask((w2, h2), 12))
        raw = Image.open(a_frames[min(i, len(a_frames) - 1)]).convert("RGB")
        ax, ay, apressed = a_meta[min(i, len(a_meta) - 1)]
        body, bezel, status = phone(raw)
        if apressed:
            touch = Image.new("RGBA", body.size, (0, 0, 0, 0))
            tx, ty = ax + bezel, ay + bezel + status
            ImageDraw.Draw(touch).ellipse((tx - 22, ty - 22, tx + 22, ty + 22), fill=(90, 120, 255, 110), outline=(255, 255, 255, 200), width=2)
            body.alpha_composite(touch)
        ps = 0.72
        body = body.resize((int(body.width * ps), int(body.height * ps)), Image.LANCZOS)
        sh, pad = shadow(body.size, 34, blur=22, opacity=130)
        px, py = canvas[0] - body.width - 40, canvas[1] - body.height - 20
        frame.alpha_composite(sh, (px - pad, py - pad))
        frame.alpha_composite(body, (px, py))
        out_frames.append(frame.convert("RGB").resize((canvas[0] * 3 // 4, canvas[1] * 3 // 4), Image.LANCZOS))
    save_gif(out_frames, out)


def save_gif(frames, out: Path):
    # One palette for the whole clip (no flicker between frames), from a
    # dozen frames across it so every state's colours are in.
    picks = frames[:: max(1, len(frames) // 12)][:12]
    w, h = frames[0].width // 2, frames[0].height // 2
    sample = Image.new("RGB", (w * 3, h * 4))
    for k, f in enumerate(picks):
        sample.paste(f.resize((w, h)), ((k % 3) * w, (k // 3) * h))
    palette = sample.quantize(colors=255, method=Image.Quantize.MEDIANCUT)
    quantized = [f.quantize(palette=palette, dither=Image.Dither.NONE) for f in frames]
    out.parent.mkdir(parents=True, exist_ok=True)
    quantized[0].save(out, save_all=True, append_images=quantized[1:], duration=FRAME_MS, loop=0, optimize=True, disposal=1)
    print(f"{out}: {len(frames)} frames, {out.stat().st_size // 1024} KB")


def main():
    src, dst = Path(sys.argv[1]), Path(sys.argv[2])
    for scene in ("connect", "profiles", "node", "logs"):
        # Zooming redraws every pixel: only the short connect clip gets it, so
        # the longer ones stay light enough for a README.
        desktop_scene(src / "desktop" / scene, dst / f"windows-{scene}.gif", zoom_in=1.18 if scene == "connect" else 1.0)
        android_scene(src / "android" / scene, dst / f"android-{scene}.gif")
    hero(src / "desktop" / "connect", src / "android" / "connect", dst / "hero.gif")


if __name__ == "__main__":
    main()
