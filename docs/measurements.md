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
| Oak | 4.2 | 3.9 | 5.6 to 5.8 |
| Birch | 3.9 | 3.5 | 5.3 to 5.4 |
| Dark oak | 4.0 | 2.9 to 3.0 | 5.2 to 5.5 |

(Ranges are from run to run: Dynamic Trees grows every test tree a
little differently.)

**Winter is no longer nearly free.** Up to 0.4.0 a bare crown was flat
twig pictures, 0.7 to 0.9 faces per leaf block. Since 0.5.0 it is small
branches made of boxes: 17 faces each (5 for the main twig, 4 for each
of the three thinner ones), on the leaf spots that touch wood, which is
about a fifth of all leaf spots. That comes to 2.9 to 3.9 faces per leaf
block, half to two thirds of what the unmodded game draws for the same
tree in leaf. The faces are small and solid (bark has no holes), so they
fill far fewer pixels than leaves do; what that means in frames is for a
real machine. The settings that lower it:

| Setting | Winter, faces per leaf block (the scene below) |
| --- | --- |
| Default: three thinner twigs, near branches | 4.0 |
| `winterTwigs = NONE` | 0.2 |
| `winterTwigs = MORE` | 5.6 |

`twigForks` 2, 1 and 0 take off about a quarter, a half and seven tenths
of the default (13, 9 and 5 faces a small branch instead of 17; worked
out, not measured).

The costliest days are the middle of the week of new leaves in spring:
young leaves are small clumps, and a small clump shows more of its sides
than a full leaf block that its neighbours cover, and the small branches
are still there beside them. Still under the 6 of the plain cubes, for
every tree on every day. With `winterTwigs = MORE` and no face limit
that is not so: 7.4 to 8.4 on the costliest day.

**A whole scene through the year** (the self test, about 20,000 leaf
blocks of oak, birch and dark oak that do not all change on the same
day, default settings, faces per leaf block):

| Summer | Autumn 9 | 10 | 11 | 12 | 13 | 14 | Winter | Spring 1 | 2 | 3 | 4 | 5 | 6 | 7 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 4.1 | 4.2 | 4.3 | 4.6 | 4.5 | 4.1 | 4.0 | 4.0 | 4.2 | 4.7 | 5.0 | 4.8 | 4.7 | 4.5 | 4.1 |

So a forest costs about the same number of faces all year round, 4 to 5
per leaf block, and a third less than the unmodded game on its
costliest day.

Where leaves cannot be see-through (fast graphics, or Sodium's fast
leaves) a crown is drawn from its outside only: 1.9 faces per leaf block
in summer, 3.9 in winter (the small branches are the same).

---

## What the server does, through the year

One scene, about 20,000 leaf blocks, 530 chunks in reach of the player,
walked from summer to the next summer. Every line is one round of the
walker over every chunk in reach, on the day named.

| The day | Spots swapped | Chunks looked at | Sections searched block by block | Server time |
| --- | --- | --- | --- | --- |
| Autumn 8, the first leaves fall | 0 | 573 | 61 | 22.7 ms |
| Autumn 11 | 6,168 | 548 | 81 | 28.1 ms |
| Autumn 12, the busiest day | 7,380 | 592 | 82 | 32.2 ms |
| Autumn 14, the last ones | 331 | 529 | 61 | 12.1 ms |
| Winter, nothing to do | 0 | 529 | **0** (61 counted) | 18.5 ms |
| Spring 2, before the leaves return | 0 | 529 | 61 | 8.2 ms |
| Spring 5 | 6,607 | 593 | 82 | 14.6 ms |
| Spring 8, all back in leaf | 0 | 529 | **0** (61 counted) | 4.0 ms |

(Taken with 0.5.0. The server's work is the same as in 0.4.0; only the
days of the birch moved, because it sheds from the far edge inward now.
The rows of autumn and winter are from one run and those of spring from
another.)

Read it like this. The counters are exact: in winter, and once the leaves
are back, not one section is searched block by block; the 61 sections
that hold the scene's crowns are counted instead. The times are not
exact. While the server walks, the player's game is drawing the whole
scene anew, in software, on the same four cores, so two rounds that do
the very same work came out at 4.0 and 18.5 ms (counted), and at 8.2 and
22.7 ms (searched with nothing to swap). A single line of this table
says how large a round is, not which of two rounds is the cheaper.

Over the whole year of a run: 19,450 spots made bare, the same number
given their leaves back, 15,851 chunks looked at in 20 rounds, 960
sections searched, 122 counted, 323.5 ms of server time in all. That is
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

**A whole forest at once.** The busiest day swapped 7,380 blocks. With
the default limit of 300 blocks a tick that is 25 ticks, about one and a
quarter seconds, and the trees nearest the player go first. Nothing waits
for it: a tree is drawn bare from the first frame, whether its blocks
have been swapped yet or not.

---

## The leaves in the air

In the same scene, over the days of the fall, with the default settings
(amount 3 of 10, within 24 blocks, at most 200 at once, 3 leaves from a
leaf block that changes in view): 950 to 1,230 leaves came down in a
run, at most 29 to 32 on their way at once. Nearly all of them are the
leaves that a leaf block lets go when it changes in front of the player
(new in 0.5.0): 910 to 1,190 leaves from 300 to 400 leaf blocks. The
leaves that drift down by themselves are 35 to 45 a run, as in 0.4.0.

Finding the leaf blocks that changed in view took 14 to 19 thousandths
of a second for the whole fall, over all of its 28 steps of the leaf
calendar together, on this machine. It is spread over ticks (4 chunk
sections searched and 48 leaf blocks looked at per tick at most), so no
single tick carries it.

The scene is a handful of trees around a fixed camera; a real forest
gives more, and the limit of 200 is what holds it. Outside the days
when leaves fall the whole thing is one test of the leaf calendar per
tick and nothing else.

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
