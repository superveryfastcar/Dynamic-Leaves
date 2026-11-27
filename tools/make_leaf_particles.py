#!/usr/bin/env python3
"""Draws the falling leaf pictures of the Foliage mod.

While the leaves fall in autumn, each player's game lets single leaves drift
down from the crowns. A falling leaf is drawn from one of these pictures,
tinted in the color the tree's leaves have that day, so one set of pictures
serves every kind of tree and every day of the year.

There are four leaves, each 8 by 8 pixels:

    falling_leaf_0   a round leaf, seen flat
    falling_leaf_1   the same leaf, seen at an angle (narrower)
    falling_leaf_2   a long leaf with a stalk
    falling_leaf_3   a small leaf, nearly edge-on

They are drawn in grays: the game multiplies them by the color of the tree,
so a gray of 230 comes out as the leaf color itself and a gray of 150 as a
shaded part of the leaf. Nothing in them is colored, or the tint would be
wrong.

The shapes are lists of rows below, one character per pixel:

    '.' nothing (see-through)      'l' the leaf                 'd' its shaded side
    'v' its vein                   's' its stalk

To change a leaf, edit its rows and run, from the project folder:

    python3 tools/make_leaf_particles.py

Needs Python 3 with the Pillow library (pip install pillow). The pictures are
written to src/main/resources/assets/foliage/textures/block/ and are part
of the repository, so nobody has to run this to build the mod.
"""
import os

from PIL import Image

# The gray of each kind of pixel, and how see-through it is.
COLORS = {
    ".": (0, 0, 0, 0),
    "l": (230, 230, 230, 255),
    "d": (150, 150, 150, 255),
    "v": (120, 120, 120, 255),
    "s": (110, 110, 110, 255),
}

LEAVES = {
    # A round leaf seen flat: widest in the middle, with a vein and a stalk.
    "falling_leaf_0": [
        "..lll...",
        ".llllld.",
        "llvllldd",
        "llvlllld",
        ".lvllld.",
        "..vlld..",
        "..s.....",
        "........",
    ],
    # The same leaf seen at an angle: half as wide.
    "falling_leaf_1": [
        "...ll...",
        "..llld..",
        "..lvld..",
        "..lvld..",
        "..llld..",
        "...lld..",
        "...s....",
        "........",
    ],
    # A long leaf with a stalk, as a birch or cherry leaf.
    "falling_leaf_2": [
        "....ll..",
        "...llld.",
        "..lllld.",
        "..lvlld.",
        ".llvld..",
        ".llld...",
        ".ss.....",
        "........",
    ],
    # A small leaf, nearly edge-on.
    "falling_leaf_3": [
        "........",
        "...ll...",
        "..llld..",
        "..llld..",
        "...lld..",
        "...s....",
        "........",
        "........",
    ],
}

OUT = os.path.join("src", "main", "resources", "assets", "foliage", "textures", "block")


def draw(rows):
    """Turns the rows of a leaf into an 8 by 8 picture."""
    image = Image.new("RGBA", (8, 8), COLORS["."])
    pixels = image.load()
    for y, row in enumerate(rows):
        if len(row) != 8:
            raise ValueError("a row must have 8 characters, not %d: %r" % (len(row), row))
        for x, kind in enumerate(row):
            if kind not in COLORS:
                raise ValueError("unknown pixel %r" % kind)
            pixels[x, y] = COLORS[kind]
    return image


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, rows in LEAVES.items():
        if len(rows) != 8:
            raise ValueError("%s must have 8 rows" % name)
        path = os.path.join(OUT, name + ".png")
        draw(rows).save(path)
        print("wrote", path)


if __name__ == "__main__":
    main()
