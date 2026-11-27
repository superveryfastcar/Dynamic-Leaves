"""Tree shapes with limbs in layers, written as tree codes for Dynamic Trees.

The owner's wish (2026-11-27): the tree's own branches should leave the trunk
"in layers", level, starting low ("add more from the base"), and no branch
should climb "more than one block up". After the first pictures in the game he
also wanted nothing on the tree to go straight up for more than about two
thirds of a block, so a limb does not step up at all: off the trunk every
piece of wood is level. A limb carries side branches at right angles, some
with a bend, "each with leaves in them". The crown keeps the
volume of Dynamic Trees' own trees, without every space filled with leaves.

How a tree is made here:

  1. A straight trunk.
  2. Limbs: on the heights from the lowest branch to just under the top, a
     level limb to both sides or to one (a few heights get none). They run
     east-west on one height and north-south on the next, because wood that
     touches other wood grows together in Dynamic Trees. Limbs are longest
     low down and get shorter toward the top. No limb is more than one block
     longer than what covers it from above, so none sticks out from under
     the crown.
  3. Side branches: off each limb at right angles, on alternating sides,
     reaching about as far as the crown is wide there. Some turn at their end
     to run on beside the limb (a bend).

  4. Ends that would get no leaves (deep inside the crown) are cut back,
     because Dynamic Trees lets a branch end without leaves rot away.

Dynamic Trees then puts a cluster of leaves on every branch end by itself.

Run it from the project folder to write the shapes into the mod:

    python3 tools/limbs/layers.py write

or to look at shapes without changing anything:

    python3 tools/limbs/layers.py preview <radius> <out.png> [species]   a sheet of drawings
    python3 tools/limbs/layers.py stats [species]                        counts per radius
    python3 tools/limbs/layers.py show <file> oak:5:0 oak:7:1 ...        a photo session in the game:
        then ./gradlew runClient -Pdynamicleaves.show=<file>
        (add dates of the year as date=autumn-11; without any: summer 7 and winter 1)

Needs Python 3 with the Pillow library for the pictures.
"""

import math
import os
import random
import statistics
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import jocode  # noqa: E402
from jocode import UP, NORTH, SOUTH, WEST, EAST  # noqa: E402

#: For a limb's direction, the two level directions at right angles to it.
SIDES = {NORTH: (WEST, EAST), SOUTH: (EAST, WEST), WEST: (SOUTH, NORTH), EAST: (NORTH, SOUTH)}

#: The numbers of each tree kind. All lengths are in blocks.
#:   height        trunk blocks, plus one pick from `height_spread`
#:   lowest        height of the lowest limb, plus 0 or 1
#:   reach         the longest limb for each room (radius) Dynamic Trees may give the tree
#:   crown         how the limbs' length runs from the lowest layer (0) to the top (1):
#:                 a list of (position, share of the reach) points, read in a straight line between them.
#:                 The lowest limbs are short on purpose: a long limb under the crown sticks out of it
#:                 (the owner called that a bug).
#:   two_limbs     chance that a layer has a limb on both sides (else on one side, or see no_limb)
#:   no_limb       chance that a layer has none (never two empty layers in a row)
#:   side_reach    the longest side branch
#:   side_every    blocks between side branches along a limb (a pick from the list)
#:   bend          chance that a side branch turns at its end to run beside the limb
#:   smother       Dynamic Trees' number for the species: leaves under this many leaves are taken out
SHAPES = {
    'oak': dict(height=9, height_spread=(0, 1, 1, 2), lowest=3, reach={2: 1, 3: 2.6, 4: 2.8, 5: 4, 6: 4.4, 7: 5, 8: 5},
                crown=[(0.0, 0.7), (0.35, 1.0), (0.65, 0.9), (1.0, 0.35)],
                two_limbs=0.9, no_limb=0.03, side_reach=2, side_every=(2, 3, 3), bend=0.4,
                smother=4, tapering=0.3),
    'birch': dict(height=11, height_spread=(0, 1, 2), lowest=4, reach={2: 1, 3: 2.2, 4: 3, 5: 3.8, 6: 4.3, 7: 4.5, 8: 4.8},
                  crown=[(0.0, 0.7), (0.35, 1.0), (0.7, 0.85), (1.0, 0.35)],
                  two_limbs=0.85, no_limb=0.05, side_reach=1, side_every=(2, 3), bend=0.3,
                  smother=4, tapering=0.1),
    # The tall birch of old growth birch forests: a long bare trunk under a slim crown.
    'tall_birch': dict(height=17, height_spread=(0, 1, 2, 3), lowest=9, reach={2: 1, 3: 2, 4: 3, 5: 3.6, 6: 4, 7: 4, 8: 4},
                       crown=[(0.0, 0.7), (0.35, 1.0), (0.7, 0.85), (1.0, 0.35)],
                       two_limbs=0.7, no_limb=0.15, side_reach=1, side_every=(2, 3), bend=0.3,
                       smother=4, tapering=0.05),
}
# Apple oak and swamp oak are oaks with something extra (apples, vines): the same shape.
SHAPES['apple_oak'] = SHAPES['oak']
SHAPES['swamp_oak'] = SHAPES['oak']

#: How many shapes are written per radius. Dynamic Trees itself stores 16 to 20.
PER_RADIUS = 20
#: Where the shapes go: one file per species, read by the mod's `tree.LayeredLimbs`.
OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..',
                       'src', 'main', 'resources', 'data', 'dynamicleaves', 'tree_shapes', 'dynamictrees')


def _along(points, t):
    """The value of a line through `points` [(position, value)] at position t."""
    for (t0, v0), (t1, v1) in zip(points, points[1:]):
        if t <= t1:
            return v0 + (v1 - v0) * (t - t0) / (t1 - t0) if t1 > t0 else v1
    return points[-1][1]


def layered(radius, rng, shape):
    """One tree with limbs in layers, as a `jocode.TreeBuilder`.

    radius: the room the tree has around its trunk (Dynamic Trees gives 2 to 8).
    rng:    a `random.Random`; the same seed gives the same tree.
    shape:  the numbers of the tree kind, see SHAPES.
    """
    tree = jocode.TreeBuilder()
    height = shape['height'] + rng.choice(shape['height_spread'])
    lowest = min(shape['lowest'] + rng.choice((0, 0, 1)), height - 3)
    reach = shape['reach'][radius]
    small = radius <= 2                               # hardly any room: a slim tree with few, short limbs

    # 1. The trunk: straight up from the first block at height 1.
    tree.run(jocode.BASE, UP, height - 1)

    # 2. The limbs, layer by layer from the bottom. East-west limbs on one layer, north-south
    #    on the next: limbs right above each other never touch, and from any side the tree
    #    shows a limb to the left and to the right on every second height.
    layers = list(range(lowest, height))              # the trunk's top block stays free: it is the tip
    turn = rng.randrange(2)
    empty_before = True                               # never two empty layers in a row, none at the bottom
    plan = {}                                         # (height, direction) -> length of the limb there
    limbs = []                                        # (direction, blocks of the limb)
    for index, y in enumerate(layers):
        t = index / max(1, len(layers) - 1)
        length = max(1, round(reach * _along(shape['crown'], t)))
        pair = ((EAST, WEST), (NORTH, SOUTH))[(index + turn) % 2]
        roll = rng.random()
        if roll < (0.4 if small else shape['no_limb']) and not empty_before and index < len(layers) - 1:
            count = 0
        elif not small and roll < shape['no_limb'] + shape['two_limbs']:
            count = 2
        else:
            count = 1
        empty_before = count == 0
        for direction in rng.sample(pair, count):
            plan[(y, direction)] = max(1, length - rng.choice((0, 0, 0, 1)))

    # No limb may stick out from under the crown (the owner called one that did a bug). In the
    # lower half of the crown, where it should get wider upward or stay as wide, a limb is at
    # most one block longer than what covers it from above: the limb two heights up on its own
    # side, the limb four heights up less one, or the side branches of the turned layer right
    # above it (two blocks of leaves at least). The upper half is the crown's roof, which gets
    # narrower upward by itself. From the top down, so that a limb made shorter counts as
    # shorter for the limb under it.
    for (y, direction) in sorted(plan, reverse=True):
        if (y - layers[0]) * 2 > layers[-1] - layers[0]:
            continue
        cover = max(plan.get((y + 2, direction), 0), plan.get((y + 4, direction), 0) - 1,
                    2 if any(at == y + 1 for at, _ in plan) else 0)
        plan[(y, direction)] = min(plan[(y, direction)], cover + 1)

    for (y, direction), own in plan.items():
        run = tree.run((0, y, 0), direction, own)
        if run:
            limbs.append((direction, run))

    # 3. Side branches, on the long limbs first so the short ones fit in around them.
    for direction, run in sorted(limbs, key=lambda limb: -len(limb[1])):
        side = rng.randrange(2)
        at = 1 if len(run) > 2 else len(run) - 1
        while at < len(run):
            start = run[at]
            out = max(abs(start[0]), abs(start[2]))   # how far out on the limb we are
            if at == len(run) - 1 and len(run) > 1:
                break                                 # the limb's tip is an end by itself
            # As far sideways as the crown is wide here, and no further than we are out
            # from the trunk: the corners between two limbs are shared.
            width = math.sqrt(max(1.0, (len(run) + 1) ** 2 - out ** 2))
            want = max(1, min(shape['side_reach'], round(min(out, width)) + rng.choice((-1, 0, 0))))
            way = SIDES[direction][side]
            grown = tree.run(start, way, want)
            if grown and rng.random() < shape['bend']:
                # A bend runs on beside the limb, but never out past the limb's own tip:
                # nothing may poke out of the crown.
                room = len(run) - 1 - at
                tree.run(grown[-1], direction, min(room, rng.choice((1, 1, 2))))
            side = 1 - side
            at += rng.choice(shape['side_every'])

    # 4. No end without leaves. Dynamic Trees lets a branch end rot away when no leaf block
    #    is next to it, and deep inside a crown the leaves are taken out again (see
    #    jocode.leaves). Such ends are cut back here until every end that is left has leaves:
    #    the tree then stands in the game exactly as it was made.
    while True:
        wood = tree.blocks()
        grown = jocode.leaves(wood, radius, shape['smother'])
        bare = [pos for pos in wood if pos != jocode.BASE and not tree.children[pos]
                and not any(n in grown for n in jocode.neighbors(pos))]
        if not bare:
            break
        for pos in bare:
            tree.cut(pos)
    return tree


def measure(tree, radius, shape):
    """Counts of one tree: wood, ends, leaves, steps up off the trunk, height, reach."""
    wood = tree.blocks() if isinstance(tree, jocode.TreeBuilder) else set(tree)
    grown = jocode.leaves(wood, radius, shape['smother'])
    off_trunk = [p for p in wood if not (p[0] == 0 and p[2] == 0)]
    return dict(
        wood=len(wood),
        ends=sum(1 for p in wood if p != jocode.BASE and sum(n in wood for n in jocode.neighbors(p)) == 1),
        leaves=len(grown),
        rises=sum(1 for p in off_trunk if (p[0], p[1] - 1, p[2]) in wood),
        height=max(p[1] for p in wood),
        reach=max(max(abs(p[0]), abs(p[2])) for p in wood),
        wood_set=wood, leaf_blocks=grown)


def seeded(species, radius, number):
    """The random source of tree `number` of a species and radius: always the same tree."""
    return random.Random(f"{species}/{radius}/{number}")


def main(argv):
    """See the top of the file."""
    if len(argv) >= 4 and argv[1] == 'preview':
        import preview
        radius, path = int(argv[2]), argv[3]
        species = argv[4] if len(argv) > 4 else 'oak'
        shape = SHAPES[species]
        trees = []
        for number in range(4):
            m = measure(layered(radius, seeded(species, radius, number), shape), radius, shape)
            trees.append((m['wood_set'], m['leaf_blocks']))
            print({k: v for k, v in m.items() if not isinstance(v, (set, dict))})
        preview.sheet(trees, path)
        return
    if len(argv) >= 2 and argv[1] == 'stats':
        species = argv[2] if len(argv) > 2 else 'oak'
        shape = SHAPES[species]
        for radius in range(2, 9):
            rows = [measure(layered(radius, seeded(species, radius, n), shape), radius, shape) for n in range(16)]
            print(f"{species} radius {radius}: " + ", ".join(
                f"{key} {statistics.mean(r[key] for r in rows):.1f}"
                for key in ('leaves', 'wood', 'ends', 'rises', 'height', 'reach')))
        return
    if len(argv) >= 2 and argv[1] == 'write':
        write_shapes()
        return
    if len(argv) >= 3 and argv[1] == 'show':
        write_show(argv[2], argv[3:])
        return
    print(__doc__)


def write_shapes():
    """Writes the shape files of every species in SHAPES into the mod's resources.

    Every code is checked before it is written: read back, it must give exactly the wood it was
    made from, and off the trunk no wood may stand on wood (every limb is level).
    """
    os.makedirs(OUT_DIR, exist_ok=True)
    for species, shape in SHAPES.items():
        lines = [
            f"# Tree shapes with limbs in layers for dynamictrees:{species}.",
            "# Written by tools/limbs/layers.py write. Do not edit by hand: change the numbers there.",
            "# A line is radius:code, as in Dynamic Trees' own jo_codes files.",
        ]
        for radius in range(2, 9):
            seen = set()
            number = 0
            while len(seen) < PER_RADIUS:
                tree = layered(radius, seeded(species, radius, number), shape)
                number += 1
                code = tree.code()
                if code in seen:
                    continue
                wood = tree.blocks()
                assert set(jocode.wood_blocks(code)) == wood, f"{species} {radius} {number}: code does not read back"
                for limb_block in wood:
                    under = jocode.step(limb_block, jocode.DOWN)
                    off_trunk = limb_block[0] != 0 or limb_block[2] != 0
                    assert not (off_trunk and under in wood), f"{species} {radius} {number}: a limb steps up"
                seen.add(code)
                lines.append(f"{radius}:{code}")
        path = os.path.join(OUT_DIR, species + '.txt')
        with open(path, 'w') as file:
            file.write("\n".join(lines) + "\n")
        print(f"wrote {os.path.normpath(path)}: {len(lines) - 3} shapes")


def write_show(path, wanted):
    """Writes the file for a photo session in the game (see DevTreeShow in the mod's dev package).

    wanted: trees as `species:radius:number` (a tree made here) or `species=code` (any code),
            planted in a row from west to east, each photographed from four spots; and dates
            of the Calendar mod as `date=autumn-11`. Without a date: summer 7 and winter 1.
    """
    lines = ["# Written by tools/limbs/layers.py show. Read by DevTreeShow."]
    views = []
    dates = ["date " + want[5:].replace('-', ' ') for want in wanted if want.startswith('date=')]
    wanted = [want for want in wanted if not want.startswith('date=')]
    for index, want in enumerate(wanted):
        if '=' in want:
            species, code = want.split('=', 1)
        else:
            species, radius, number = want.split(':')
            code = layered(int(radius), seeded(species, int(radius), int(number)), SHAPES[species]).code()
        x, z = index * 24, -13
        lines.append(f"tree dynamictrees:{species} {code} {x} {z} 0")
        # The cameras stand further back and higher for a taller tree, so that a birch fits
        # in the picture as an oak does. The ground is at height -61, the trunk starts at -60.
        top = max(block[1] for block in jocode.wood_blocks(code)) + 2      # the leaves go two above the wood
        back = max(1.0, (top + 1) / 12.5)
        # From the south at half height, from the south-east corner a little above, from the
        # ground looking up, and from close by at half height (for the twigs).
        views.append(f"view {index + 1}-front {x + 0.5} {-60 + top * 0.33:.1f} {z + 12.5 * back:.1f} 180 4")
        views.append(f"view {index + 1}-corner {x + 9.5 * back:.1f} {-60 + 7 * back:.1f} {z + 9.5 * back:.1f} 135 14")
        views.append(f"view {index + 1}-ground {x + 4.5} -60 {z + 7.5} 149 -30")
        views.append(f"view {index + 1}-close {x + 3.5} {-60 + top * 0.5:.1f} {z + 6.5} 152 -5")
    lines += views
    lines += dates or ["date summer 7", "date winter 1"]
    with open(path, 'w') as file:
        file.write("\n".join(lines) + "\n")
    print(f"wrote {path}: {len(wanted)} trees, {len(views)} views, {len(dates) or 2} dates")


if __name__ == '__main__':
    main(sys.argv)
