# Foliage: what it costs

Every number here was measured, not guessed. Where a number is missing,
it says so and why.

**The machine.** All of these come from the build machine, which draws
without a graphics card (software drawing) and has four cores. So the
times are upper numbers: a real machine is faster, and frame times are
not measured here at all. Frames belong on a real machine, with the
mod and Distant Horizons installed, and are still open.

**How they were taken.** The faces per leaf block come from the game
tests (`./gradlew runGameTestServer`), which plant real Dynamic Trees
trees by command and count what the chunk builder is handed. The rest
comes from the self test (`./gradlew runClient -Pfoliage.selfTest=true`),
which plants a fixed scene of about 20,000 leaf blocks, in a world with
about 530 chunks in reach, and walks it through a whole year, taking the
server's own counters at every look.

---

## Faces per leaf block

The unmodded game draws 6 faces for every leaf block, including the ones
buried inside the crown. Lone full-grown trees, `maxFaces` 6:

| Tree | Plain | Shell | Bushy | Tufts | Mixed |
| --- | --- | --- | --- | --- | --- |
| Oak | 6.0 | 3.9 | 4.3 | 3.9 | 4.2 |
| Birch | 6.0 | 3.6 | 3.9 | 3.5 | 3.9 |
| Dark oak | 6.0 | 3.8 | 4.1 | 3.7 | 4.0 |
| Jungle | 6.0 | 3.6 | 3.9 | 3.6 | 3.9 |
| Cherry | 6.0 | 3.7 | 4.0 | 3.7 | 4.0 |
| Mangrove | 6.0 | 4.2 to 4.5 | 4.7 to 5.0 | 4.2 to 4.4 | 4.6 to 4.9 |
| Acacia | 6.0 | 4.7 to 4.8 | 5.3 to 5.4 | 4.6 to 4.7 | 5.1 to 5.2 |
| Azalea | 6.0 | 4.7 to 4.8 | 5.2 to 5.3 | 4.6 to 4.7 | 5.0 to 5.2 |
| Spruce | 6.0 | 5.5 | 5.6 | 5.3 | 5.3 |

Through the year, with the default settings:

| Tree | Summer | Winter | The costliest day of the year |
| --- | --- | --- | --- |
| Oak | 4.2 | 0.9 | 5.1 |
| Birch | 3.9 | 0.8 | 5.1 |
| Dark oak | 4.0 | 0.7 | 5.3 |

The costliest days are the middle of the week of new leaves in spring:
young leaves are small clumps, and a small clump shows more of its sides
than a full leaf block that its neighbours cover. Still under the 6 of
the plain cubes. Where leaves cannot be see-through (fast graphics, or
Sodium's fast leaves) a crown is drawn from its outside only: 1.9 faces
per leaf block.

---

## What the server does, through the year

One scene, about 20,000 leaf blocks, 530 chunks in reach of the player,
walked from summer to the next summer. Every line is one round of the
walker over every chunk in reach, on the day named.

| The day | Spots swapped | Chunks looked at | Sections searched block by block | Server time |
| --- | --- | --- | --- | --- |
| Autumn 8, the first leaves fall | 0 | 573 | 61 | 14.7 ms |
| Autumn 11 | 6,812 | 548 | 80 | 41.2 ms |
| Autumn 12, the busiest day | 7,973 | 595 | 84 | 36.2 ms |
| Autumn 14, the last ones | 328 | 529 | 61 | 7.3 ms |
| Winter, nothing to do | 0 | 529 | **0** (61 counted) | 16.5 ms |
| Spring 2, before the leaves return | 0 | 529 | 61 | 9.0 ms |
| Spring 5 | 6,598 | 592 | 80 | 20.7 ms |
| Spring 8, all back in leaf | 0 | 529 | **0** (61 counted) | 6.2 ms |

Read it like this. The counters are exact: in winter, and once the leaves
are back, not one section is searched block by block; the 61 sections
that hold the scene's crowns are counted instead. The times are not
exact. While the server walks, the player's game is drawing the whole
scene anew, in software, on the same four cores, so two rounds that do
the very same work came out at 6.2 and 16.5 ms (counted), and at 9.0,
14.7 and 17.5 ms (searched with nothing to swap). A single line of this
table says how large a round is, not which of two rounds is the cheaper.

Over the whole year of the run: 19,369 spots made bare, the same number
given their leaves back, 15,834 chunks looked at in 20 rounds, 956
sections searched, 122 counted, 339.8 ms of server time in all. That is
a third of a second of one core for a whole year of a forest.

**What counting saves, measured where nothing else is running.** A game
test (`countingASectionCostsLessThanSearchingIt`) takes one chunk with an
oak's crown in it on the test server, which has no player and draws
nothing, and looks at it 700 times on a day in the middle of the fall,
when the section with the crown has to be searched block by block, and
700 times in winter, when its blocks are only counted. Nothing is
swapped either way. The fastest of seven goes of a hundred looks, in
each of 24 runs of the test:

| | One look at the chunk |
| --- | --- |
| Searched block by block (the fall) | 42 to 82 millionths of a second |
| Counted (winter, and summer after a winter) | 12 to 31 millionths of a second |

**Counting is between a sixth and a half of the cost of searching, and a
third in the middle run of the 24.** That is what the weeks of winter and
the months of summer cost, when nothing changes. The spread is the
machine's: the test server shares its four cores with whatever else the
build machine is doing.

**A whole forest at once.** The busiest day swapped 7,973 blocks. With
the default limit of 300 blocks a tick that is 27 ticks, about one and a
half seconds, and the trees nearest the player go first. Nothing waits
for it: a tree is drawn bare from the first frame, whether its blocks
have been swapped yet or not.

---

## The leaves in the air

In the same scene, over the days of the fall: 42 to 46 leaves came down
in each of three runs, at most 12 to 19 on their way at once, with the
default settings (amount 3 of 10, within 24 blocks, at most 200 at
once). The scene is a handful of trees around a fixed camera; a real
forest gives more, and the limit of 200 is what holds it. Outside the
days when leaves fall the whole thing is one test of the leaf calendar
per tick and nothing else.

The game's own weather holds a few hundred raindrops at a time, so 200
leaves cost about as much as light rain.

---

## What is not measured here

- **Frames.** This machine draws in software. Frames per second, and
  what Sodium makes of the crowns, want a real machine.
- **Distant Horizons.** There is no Distant Horizons in this container,
  so the link is proved only by reading its API and by a test that a
  game without the mod is untouched.
- **Snow and fruit** that come down with the leaves are proved by the
  game tests, with trees, snow and apples planted for them (snow falls
  past the bare blocks on its way down, fruit that has grown drops and
  fruit that has only just set goes quietly); the self test scene has
  none of it, so its counters for both are zero.
- **The way out.** `/foliage restore` is proved by the game tests from
  start to stop, and its reading of a region file's table of contents by
  a region file written by hand; what a sweep of a long-played world
  costs in time is still open, because this container has no such world.
- **A real forest on a server with players.** The scene is one player
  standing in a planted wood. Land that has never been visited, many
  players at once and a long-played world are still open.
