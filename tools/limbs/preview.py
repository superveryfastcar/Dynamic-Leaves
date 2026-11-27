"""Draws a tree model as a picture, to judge a shape before it goes into the game.

The tree is drawn from a corner, slightly from above (an "isometric" view),
block by block from the back to the front. Wood is drawn as thick as Dynamic
Trees makes it, leaves as whole cubes. It is a rough look at the shape and
the volume only: the real leaves, with Foliage's leaf shapes, are in the game.
"""

from PIL import Image, ImageDraw

import jocode

#: Pixels per block.
SCALE = 20
#: Colors: (top, right side, left side).
WOOD = ((134, 96, 56), (108, 76, 43), (84, 58, 32))
LEAF_SHADES = [((92, 160, 70), (70, 132, 52), (52, 106, 40)),
               ((84, 150, 64), (64, 124, 48), (48, 100, 37)),
               ((100, 168, 78), (76, 138, 58), (58, 112, 44))]
SKY = (206, 226, 246)
GRASS = (124, 168, 92)


def _turn(pos, quarter_turns):
    """A position turned around the trunk by quarter turns (to look from another side)."""
    x, y, z = pos
    for _ in range(quarter_turns % 4):
        x, z = -z, x
    return (x, y, z)


class Canvas:
    """Collects boxes and paints them back to front."""

    def __init__(self, width, height, ground_y, tilt=0.5):
        self.image = Image.new("RGB", (width, height), SKY)
        self.draw = ImageDraw.Draw(self.image)
        self.origin = (width // 2, ground_y)
        self.tilt = tilt            # how much we look from above: 0.5 is the classic view, lower is flatter
        self.boxes = []

    def point(self, x, y, z):
        """Where a point of the world lands on the picture."""
        return (self.origin[0] + (x - z) * SCALE * 0.87,
                self.origin[1] + (x + z) * SCALE * self.tilt - y * SCALE * (1.0 - self.tilt * 0.3))

    def box(self, low, high, colors):
        """Queues a box from corner `low` to corner `high`."""
        depth = sum(low) + sum(high)
        self.boxes.append((depth, low, high, colors))

    def paint(self):
        """Paints every queued box, the far ones first."""
        for _, (x0, y0, z0), (x1, y1, z1), (top, right, left) in sorted(self.boxes, key=lambda b: b[0]):
            p = self.point
            self.draw.polygon([p(x0, y1, z0), p(x1, y1, z0), p(x1, y1, z1), p(x0, y1, z1)], fill=top)
            self.draw.polygon([p(x1, y0, z0), p(x1, y1, z0), p(x1, y1, z1), p(x1, y0, z1)], fill=right)
            self.draw.polygon([p(x0, y0, z1), p(x1, y0, z1), p(x1, y1, z1), p(x0, y1, z1)], fill=left)
        self.boxes = []


def draw_tree(wood, leaf_blocks, quarter_turns=0, with_leaves=True, size=(420, 400), tilt=0.5):
    """One picture of a tree. `wood` is a set of positions, `leaf_blocks` the leaves from `jocode.leaves`."""
    canvas = Canvas(size[0], size[1], size[1] - 70, tilt)
    # A patch of ground under the tree.
    p = canvas.point
    canvas.draw.polygon([p(-7, 1, -7), p(8, 1, -7), p(8, 1, 8), p(-7, 1, 8)], fill=GRASS)
    radius = jocode.thickness(wood)
    turned = {_turn(pos, quarter_turns): pos for pos in wood}
    for pos, original in turned.items():
        half = radius[original] / 16.0
        cx, cy, cz = pos[0] + 0.5, pos[1] + 0.5, pos[2] + 0.5
        canvas.box((cx - half, cy - half, cz - half), (cx + half, cy + half, cz + half), WOOD)
        # A bar toward every thinner or equal neighbor, so the wood reads as one limb.
        for neighbor in jocode.neighbors(pos):
            if neighbor in turned and radius[turned[neighbor]] <= radius[original]:
                thin = radius[turned[neighbor]] / 16.0
                low = [min(a, b) + 0.5 - thin for a, b in zip(pos, neighbor)]
                high = [max(a, b) + 0.5 + thin for a, b in zip(pos, neighbor)]
                # Cut the bar in two so each half sorts with its own block.
                mid_low = [(a + b) / 2 for a, b in zip(low, high)]
                for axis in range(3):
                    if pos[axis] != neighbor[axis]:
                        a_low, a_high = list(low), list(high)
                        b_low, b_high = list(low), list(high)
                        a_high[axis] = mid_low[axis]
                        b_low[axis] = mid_low[axis]
                        canvas.box(tuple(a_low), tuple(a_high), WOOD)
                        canvas.box(tuple(b_low), tuple(b_high), WOOD)
    if with_leaves:
        for original in leaf_blocks:
            x, y, z = _turn(original, quarter_turns)
            shade = LEAF_SHADES[(original[0] * 7 + original[1] * 13 + original[2] * 29) % 3]
            canvas.box((x, y, z), (x + 1, y + 1, z + 1), shade)
    canvas.paint()
    return canvas.image


def sheet(trees, path, turns=(0, 1), labels=None):
    """A sheet of pictures: one row per tree, bare and in leaf from each side in `turns`.

    trees: list of (wood, leaf_blocks).
    """
    cell = (420, 400)
    columns = len(turns) * 2
    out = Image.new("RGB", (cell[0] * columns, cell[1] * len(trees)), (255, 255, 255))
    for row, (wood, leaf_blocks) in enumerate(trees):
        for index, turn in enumerate(turns):
            out.paste(draw_tree(wood, leaf_blocks, turn, False, cell), (cell[0] * (index * 2), cell[1] * row))
            out.paste(draw_tree(wood, leaf_blocks, turn, True, cell), (cell[0] * (index * 2 + 1), cell[1] * row))
        if labels:
            ImageDraw.Draw(out).text((8, cell[1] * row + 6), labels[row], fill=(0, 0, 0))
    out.save(path)
