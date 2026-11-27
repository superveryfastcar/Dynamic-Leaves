#!/usr/bin/env python3
"""Draws the twig pictures of the Foliage mod.

A twig picture is a 16 by 16 block texture with a bare twig that grows from
the middle of its bottom edge and forks on its way up. The mod puts two of
them crosswise into a leaf block wherever a twig shows: on the branches of a
bare tree in winter, and under the small leaf clumps at the edge of a crown.

There are four twigs, each in three bark colors:

    twigs_brown_0..3   oak, jungle, acacia, mangrove, azalea
    twigs_birch_0..3   birch (the twigs mirrored, so birch and oak differ)
    twigs_dark_0..3    dark oak, spruce, cherry

Each also comes with buds, for the first days of spring: the same twig with
a bud at every tip and a few along its sides.

    twigs_brown_buds_0..3 and so on

The twigs were drawn for this mod. Each is a list of lines (below): the
stem first, then its side twigs, each from point to point, where x runs
from 0 at the left to 15 and y from 0 at the top to 15 at the bottom. The
lines are drawn so that every pixel of a twig touches the next one along an
edge and not only at a corner; a twig that hangs together by corners only
looks like a row of dots in the game. Only the last step of a twig is left
thin.

To change a twig, edit its lines and run, from the project folder:

    python3 tools/make_twigs.py

Needs Python 3 with the Pillow library (pip install pillow). The pictures are
written to src/main/resources/assets/foliage/textures/block/ and are part of
the repository, so nobody has to run this to build the mod.
"""
import os
import random

from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                   "assets", "foliage", "textures", "block")

# Three shades per bark: the usual one, a darker one, a lighter one. Twigs
# are a little darker than the bark of their tree, so that they stand out
# against the sky.
PALETTES = {
    "brown": [(92, 72, 44), (72, 56, 34), (108, 86, 53)],
    "birch": [(84, 68, 60), (66, 53, 48), (102, 85, 74)],
    "dark": [(62, 46, 28), (47, 35, 21), (77, 58, 36)],
}

# The buds: a fresh green and a darker one, the same on every bark.
BUDS = [(170, 186, 88), (138, 160, 70)]

# One twig pixel in this many gets a bud at its side.
SIDE_BUD_EVERY = 5

# The twigs of birch are mirrored left to right.
MIRRORED = {"birch"}

# From this row down a twig is its thick stem: two pixels wide, in the darker shade.
STEM_ROW = 12

# The four twigs. The first line of each is the stem.
TWIGS = [
    [
        [(7, 15), (7, 10), (8, 6), (7, 0)],
        [(7, 12), (3, 10), (1, 7)],
        [(7, 9), (4, 5), (3, 1)],
        [(8, 13), (12, 10), (13, 7)],
        [(8, 6), (11, 4), (12, 1)],
        [(4, 5), (1, 4)],
        [(11, 4), (14, 3)],
    ],
    [
        [(8, 15), (8, 11), (6, 7), (5, 2), (4, 0)],
        [(8, 12), (11, 10), (13, 6)],
        [(7, 8), (10, 5), (11, 1)],
        [(8, 13), (4, 12), (2, 9)],
        [(6, 6), (3, 5), (1, 2)],
        [(11, 10), (14, 9)],
        [(10, 5), (13, 4)],
    ],
    [
        [(7, 15), (7, 11), (9, 7), (10, 3), (10, 0)],
        [(7, 12), (4, 9), (3, 5)],
        [(8, 8), (6, 4), (6, 1)],
        [(7, 13), (11, 12), (14, 9)],
        [(9, 6), (12, 5), (14, 2)],
        [(4, 9), (1, 8)],
    ],
    [
        [(8, 15), (8, 9), (7, 5), (7, 0)],
        [(8, 13), (5, 11), (2, 10), (0, 7)],
        [(8, 10), (4, 7), (3, 3)],
        [(8, 12), (11, 11), (13, 8), (15, 7)],
        [(8, 8), (11, 6), (12, 2)],
        [(7, 4), (9, 3), (10, 0)],
        [(11, 6), (14, 5)],
        [(4, 7), (1, 5)],
    ],
]


def line(start, end, thin_end):
    """The pixels of a straight line from `start` to `end`.

    Where the line steps sideways and upward at once, a pixel is added in
    the corner, so that the pixels touch along edges. With `thin_end` the
    last step is left without one.
    """
    (x, y), (x1, y1) = start, end
    dx, dy = abs(x1 - x), abs(y1 - y)
    sx, sy = (1 if x1 > x else -1), (1 if y1 > y else -1)
    error = dx - dy
    pixels = [(x, y)]
    while (x, y) != (x1, y1):
        twice = 2 * error
        moved_x = moved_y = False
        if twice > -dy:
            error -= dy
            x += sx
            moved_x = True
        if twice < dx:
            error += dx
            y += sy
            moved_y = True
        last = (x, y) == (x1, y1)
        if moved_x and moved_y and not (thin_end and last):
            # The corner pixel: first up, then sideways, as a twig grows.
            pixels.append((x - sx, y))
        pixels.append((x, y))
    return pixels


def twig_pixels(index):
    """All pixels of twig number `index`, as a set of (x, y)."""
    pixels = set()
    for points in TWIGS[index]:
        for number in range(len(points) - 1):
            pixels.update(line(points[number], points[number + 1], number == len(points) - 2))
    # The thick stem: a second pixel beside the stem's own.
    stem_x = TWIGS[index][0][0][0]
    for y in range(STEM_ROW + 1, 16):
        pixels.add((stem_x + 1, y))
    return {(x, y) for x, y in pixels if 0 <= x < 16 and 0 <= y < 16}


def spray(index, palette, mirrored):
    """One twig picture: twig number `index` in the shades of `palette`."""
    # The same shades every time the script runs.
    shades = random.Random(index * 13 + 5)
    picture = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for x, y in sorted(twig_pixels(index)):
        shade = palette[1] if y >= STEM_ROW else palette[shades.choice([0, 0, 1, 2])]
        picture.putpixel((15 - x if mirrored else x, y), shade + (255,))
    return picture


def with_buds(picture, index):
    """The same twig picture with buds: one at every tip, a few along the twigs."""
    out = picture.copy()
    twig = [[picture.getpixel((x, y))[3] != 0 for x in range(16)] for y in range(16)]
    # The same buds every time the script runs.
    choice = random.Random(index * 29 + 3)
    for y in range(STEM_ROW):
        for x in range(16):
            if not twig[y][x]:
                continue
            above = y > 0 and any(twig[y - 1][nx] for nx in (x - 1, x, x + 1) if 0 <= nx < 16)
            if not above:
                # The tip of a twig.
                out.putpixel((x, y), BUDS[0] + (255,))
            elif choice.randrange(SIDE_BUD_EVERY) == 0:
                # A bud at the side, where there is room for one.
                for nx in choice.sample((x - 1, x + 1), 2):
                    if 0 <= nx < 16 and not twig[y][nx] and out.getpixel((nx, y))[3] == 0:
                        out.putpixel((nx, y), BUDS[1] + (255,))
                        break
    return out


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, palette in PALETTES.items():
        for index in range(len(TWIGS)):
            bare = spray(index, palette, name in MIRRORED)
            bare.save(os.path.join(OUT, f"twigs_{name}_{index}.png"))
            with_buds(bare, index).save(os.path.join(OUT, f"twigs_{name}_buds_{index}.png"))
    print(f"wrote {2 * len(PALETTES) * len(TWIGS)} twig pictures to {os.path.normpath(OUT)}")


if __name__ == "__main__":
    main()
