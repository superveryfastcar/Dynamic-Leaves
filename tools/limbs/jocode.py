"""Dynamic Trees' stored tree shapes, read and modelled outside the game.

Dynamic Trees keeps the shape of every tree it plants in new land as a short
text, a "JoCode" (files `trees/<mod>/jo_codes/<species>.txt`, one line per
shape: `radius:code`). This module

  * turns a code into the list of wood blocks it stands for (`wood_blocks`)
    and back (`TreeBuilder.code`),
  * works out how thick each wood block becomes (`thickness`), and
  * works out which leaf blocks the tree gets (`leaves`), the way the game
    does it when it plants the tree: a leaf cluster on every branch end,
    leaves buried too deep taken out again, then three rounds of the leaves'
    own "do I still get enough water" rule.

It is a model, not the game: it was checked against Dynamic Trees 1.7.2's
code (`worldgen.JoCode`, `nodemapper.InflatorNode`, `cell.LeafClusters`,
`TreeHelper.ageVolume`) and its counts match real trees closely, but the
picture that counts is the one taken in the game.

Positions are (x, y, z) with y up, x east and z south, counted from the soil
block the tree stands on. The first wood block is (0, 1, 0).
"""

import collections

#: The 64 characters of a code. Each stands for two steps of three bits.
BASE64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

#: The six directions in the order Dynamic Trees (and the game) numbers them.
DOWN, UP, NORTH, SOUTH, WEST, EAST = range(6)
#: The step each direction takes.
STEP = [(0, -1, 0), (0, 1, 0), (0, 0, -1), (0, 0, 1), (-1, 0, 0), (1, 0, 0)]
#: The opposite of each direction.
OPPOSITE = [UP, DOWN, SOUTH, NORTH, EAST, WEST]
#: In a code, 6 opens a fork and 7 closes it.
FORK, END = 6, 7

#: The first wood block of every tree.
BASE = (0, 1, 0)


def step(pos, direction, count=1):
    """The position `count` blocks from `pos` in a direction."""
    d = STEP[direction]
    return (pos[0] + d[0] * count, pos[1] + d[1] * count, pos[2] + d[2] * count)


def neighbors(pos):
    """The six positions next to `pos`, in direction order."""
    return [step(pos, d) for d in range(6)]


# --------------------------------------------------------------------------
# Codes
# --------------------------------------------------------------------------

def decode(code):
    """A code as its list of steps (numbers 0 to 7)."""
    steps = []
    for char in code:
        index = BASE64.find(char)
        if index != -1:
            steps += [index >> 3, index & 7]
    return steps


def wood_blocks(code):
    """The wood blocks of a code, in the order the game places them."""
    steps = decode(code)
    out = []

    def fork(at, pos):
        while at < len(steps):
            s = steps[at]
            if s == FORK:
                at = fork(at + 1, pos)
            elif s == END:
                return at + 1
            else:
                pos = step(pos, s)
                out.append(pos)
                at += 1
        return at

    fork(0, (0, 0, 0))
    return out


def load_codes(path):
    """A jo_codes file as {radius: [codes]}. Root codes (lines with `*`) and comments are skipped."""
    codes = collections.defaultdict(list)
    with open(path) as file:
        for line in file:
            line = line.strip()
            if not line or line.startswith('#') or line.startswith('*') or ':' not in line:
                continue
            radius, code = line.split(':', 1)
            codes[int(radius)].append(code.strip())
    return codes


class TreeBuilder:
    """Wood blocks put together one by one, each knowing the block it grew out of.

    Dynamic Trees lets no wood block touch another one except the block it
    grew from and the blocks that grew from it: wood that touches grows
    together into a loop, and the game then throws the tree away. `grow`
    keeps that rule, so whatever this class builds is a tree the game accepts.
    """

    def __init__(self):
        self.parent = {BASE: None}      # position -> the position it grew from
        self.children = {BASE: []}      # position -> [(direction, position)] in the order added

    def free(self, pos, parent):
        """Whether wood may go here: empty, above the soil, touching no wood but its parent."""
        if pos in self.parent or pos[1] < 1:
            return False
        return all(n == parent or n not in self.parent for n in neighbors(pos))

    def grow(self, parent, direction):
        """Adds a wood block next to `parent`. Returns its position, or None if it may not go there."""
        pos = step(parent, direction)
        if not self.free(pos, parent):
            return None
        self.parent[pos] = parent
        self.children[pos] = []
        self.children[parent].append((direction, pos))
        return pos

    def run(self, start, direction, length):
        """Grows up to `length` blocks in a straight line. Returns the blocks that could be placed."""
        placed = []
        pos = start
        for _ in range(length):
            pos = self.grow(pos, direction)
            if pos is None:
                break
            placed.append(pos)
        return placed

    def cut(self, pos):
        """Takes a block and everything that grew from it out again (used to undo a try)."""
        for _, child in list(self.children[pos]):
            self.cut(child)
        parent = self.parent.pop(pos)
        del self.children[pos]
        self.children[parent] = [(d, p) for d, p in self.children[parent] if p != pos]

    def blocks(self):
        """All wood blocks."""
        return set(self.parent)

    def code(self):
        """The tree as a code: every child of a block but the last is a fork, the last carries on."""
        steps = [UP]
        # Walked with its own stack: tall trees would run into Python's limit on nested calls.
        stack = [('visit', BASE)]
        while stack:
            what, value = stack.pop()
            if what == 'emit':
                steps.append(value)
                continue
            kids = self.children[value]
            todo = []
            for direction, kid in kids[:-1]:
                todo += [('emit', FORK), ('emit', direction), ('visit', kid), ('emit', END)]
            if kids:
                direction, kid = kids[-1]
                todo += [('emit', direction), ('visit', kid)]
            stack.extend(reversed(todo))
        if len(steps) % 2:
            steps.append(END)
        return ''.join(BASE64[steps[i] << 3 | steps[i + 1]] for i in range(0, len(steps), 2))


# --------------------------------------------------------------------------
# Thickness
# --------------------------------------------------------------------------

def thickness(wood, tapering=0.3, worldgen_factor=1.5, max_radius=8):
    """How thick each wood block is, as a radius in pixels (1 to 8), the way `InflatorNode` sets it.

    An end is 1. Any other block is the square root of the summed squares of
    what grew from it, plus a little (`tapering` of the species times Dynamic
    Trees' world generation factor), rounded down, at least 2.
    """
    wood = set(wood)
    parent = {BASE: None}
    order = [BASE]
    for pos in order:                      # breadth first; `order` grows while we walk it
        for n in neighbors(pos):
            if n in wood and n not in parent:
                parent[n] = pos
                order.append(n)
    radius = {}
    for pos in reversed(order):
        grown = [n for n in neighbors(pos) if parent.get(n) == pos]
        if not grown:
            radius[pos] = 1
            continue
        area = sum(radius[n] ** 2 for n in grown)
        radius[pos] = int(max(2.0, min(max_radius, area ** 0.5 + tapering * worldgen_factor)))
    return radius


# --------------------------------------------------------------------------
# Leaves
# --------------------------------------------------------------------------

def _cluster(rows_by_height, center):
    """A leaf cluster from its layers (strings of digits) as {(dx, dy, dz): water}."""
    voxels = {}
    for y, rows in enumerate(rows_by_height):
        for z, row in enumerate(rows):
            for x, char in enumerate(row):
                if char != '0':
                    voxels[(x - center[0], y - center[1], z - center[2])] = int(char)
    return voxels


#: The leaves a branch end of an oak or birch gets: 5 wide, 4 high, the end at (2, 1, 2).
#: The digit is the leaf's "water" (hydration, 1 to 4); 0 is no leaf.
DECIDUOUS_CLUSTER = _cluster([
    ["00000", "01110", "01110", "01110", "00000"],
    ["01110", "13431", "14041", "13431", "01110"],
    ["01110", "12321", "13431", "12321", "01110"],
    ["00000", "01110", "01110", "01110", "00000"],
], (2, 1, 2))

#: The rules by which an oak or birch leaf works out its water from its six
#: neighbors (Dynamic Trees' "deciduous" cell kit). Each rule 0xABC reads: if at
#: least B neighbors give water A, this leaf has water C. The first that fits wins.
DECIDUOUS_RULES = [0x514, 0x423, 0x322, 0x411, 0x311, 0x211]


def leaves(wood, radius, smother=4, age_rounds=3, map_height=32):
    """The leaf blocks a tree gets when the game plants it, as {position: water}.

    wood:       the tree's wood blocks.
    radius:     the room the game gave the tree (2 to 8); leaves further out than
                radius + 2 from the trunk are cut off, as in the game.
    smother:    how many leaves may be stacked in a column before the ones below
                are taken out (oak and birch: 4).
    age_rounds: rounds of the leaves' own water rule after planting (the game: 3).
    """
    wood = set(wood)
    ends = [p for p in wood if p != BASE and sum(1 for n in neighbors(p) if n in wood) == 1]
    if len(wood) == 1:
        ends = [BASE]
    end_set = set(ends)
    span_x = span_z = radius * 2 + 5

    def inside(pos):
        """Whether a position is inside the box the game keeps leaves in."""
        x, y, z = pos[0], pos[1] - 1, pos[2]
        return (-(span_x // 2) <= x <= span_x - span_x // 2 - 1
                and -(span_z // 2) <= z <= span_z - span_z // 2 - 1 and 0 <= y < map_height)

    # The game's leaf map: 32 marks wood, 16 a branch end, 1 to 4 a leaf's water.
    cells = {}
    for pos in wood:
        if inside(pos):
            cells[pos] = 32
    for end in ends:
        if inside(end):
            cells[end] = 16
    for end in ends:
        for offset, water in DECIDUOUS_CLUSTER.items():
            pos = (end[0] + offset[0], end[1] + offset[1], end[2] + offset[2])
            if inside(pos) and water >= cells.get(pos, 0):
                cells[pos] = water
    # Smother: going down each column, leaves below `smother` stacked leaves go.
    if smother:
        columns = collections.defaultdict(list)
        for pos in cells:
            columns[(pos[0], pos[2])].append(pos[1])
        top = max(pos[1] for pos in cells)
        for (x, z) in columns:
            count = 0
            for y in range(top, 0, -1):
                value = cells.get((x, y, z), 0)
                if value == 0:
                    count = 0
                elif value & 15:
                    count += 1
                    if count > smother:
                        del cells[(x, y, z)]
                elif value & 16:
                    count += 1
                    if inside((x, y + 1, z)):
                        cells[(x, y + 1, z)] = 4
    grown = {pos: value for pos, value in cells.items() if value & 15 and pos not in wood}

    def water(pos):
        """The water a leaf at `pos` gets from its neighbors."""
        votes = [0] * 16
        for neighbor in neighbors(pos):
            if neighbor in wood:
                votes[5 if neighbor in end_set else 0] += 1   # only branch ends give water (5)
            elif neighbor in grown:
                votes[grown[neighbor]] += 1
            else:
                votes[0] += 1
        for rule in DECIDUOUS_RULES:
            if votes[(rule >> 8) & 15] >= ((rule >> 4) & 15):
                return rule & 15
        return 0

    for _ in range(age_rounds):
        for pos in sorted(grown):
            value = water(pos)
            if value == 0:
                del grown[pos]
            else:
                grown[pos] = value
    return grown
