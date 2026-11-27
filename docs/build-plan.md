# Foliage: build plan

Written 2026-11-27 before any code. The builder follows it and the reviewer
checks the code against it. Design for the owner to read:
https://claude.ai/artifact/AXZ8oBJWzArmH867JfoAE8

Working name Foliage, mod id `foliage`, package
`io.github.superveryfastcar.foliage`, MIT, author coolcritter22, NeoForge
21.1.267 for Minecraft 1.21.1. Same build setup as Seasons. Needs Dynamic
Trees 1.7.2 (`[1.7.2,1.8)`), Calendar 0.2.0 and Seasons, all from `libs/`.
The name is on a decision card; a different answer means a rename before the
first release.

Research notes: `/mnt/project-files/modpack/mods/leaves-addon/research/`
(`00` to `11`). Fable writes every part. Helpers only review the finished work.

## Decisions the code must follow

Owner's words (fixed):

1. An addon for Dynamic Trees that replaces the leaves with leaves that are
   "smaller or full size blocks", also on branches sticking out from the tree.
2. In winter the branches stand bare.
3. Leaves drop over about 7 days of autumn and regrow during the first 7 days
   of spring, "by first budding".
4. "All the performance optimization you can manage."
5. "Aesthetic implementation is equally as important."
6. Standing rules: every class and method documented, every number a setting,
   game tests plus an unattended real-client test plus performance
   measurements, nothing copied from other mods unless the licence allows it
   (Dynamic Trees is MIT), coolcritter22 in everything that reaches GitHub or
   the mod, vanilla biomes and trees first.

On decision cards (2026-11-27, open; built on Claude's pick until answered):

- Winter leaf spot: placeholder block with twigs near branches (pick),
  placeholder with more twigs, or plain air.
- Leaf shape: shell, bushy, tufts, mixed (pick). All four are built as a
  player setting, so the card only sets the default.
- Name: Foliage (pick), Boughs, Deciduous, Branches.

Claude's defaults, stated in the design doc under "Decided by default":
oak (with apple oak and swamp oak), birch and dark oak shed; other Dynamic
Trees leaves get the new shape only; hot biomes keep leaves (Seasons' rule);
leaves fall in the last 7 days of autumn; no shade under bare trees; a spot
that looks bare can be walked through; bare crowns drop nothing; branches of
Dynamic Trees stay as they are; trees that are not Dynamic Trees keep
Seasons' look; far trees in Distant Horizons keep full crowns in version 1;
falling leaves are particles.

## The design in five lines

- The **look** of a leaf spot is a pure function of date, tree kind, tree
  shift and spot order. The client model computes it in `getModelData` and
  hands out prebuilt quad lists. No block changes for looks.
- The **real block** is a leaf or a placeholder (the bare block), swapped
  quietly, a chunk at a time. First plan: once per transition, at night,
  plus catch-up for chunks that were away. Since stage 2: spot by spot as
  the leaf clock steps, with nothing stored (see "Stage 3 plan").
- A spot that shows leaves is always a real leaf block. A leaf whose look is
  bare has no collision and no outline.
- The placeholder is a second leaf kind of the same Dynamic Trees family, so
  Dynamic Trees sees leaves (no rot, no regrowth) while the game sees
  something close to air.
- No mixins into Dynamic Trees or Sodium.

## Stages

| # | Version | What | Proof |
| --- | --- | --- | --- |
| 1 | 0.1.0 | Shapes: sprite source for leaf cards, shape rules, leaf model, wrapping of Dynamic Trees leaves, client settings | unit-style game tests of the rules, self test with pictures and face counts, with Sodium |
| 2 | 0.2.0 | Leaf calendar: the look rule, thinned and small leaf sprites, twigs and buds, stages in the model, Seasons 0.4.0 API | game tests of the rule day by day, self test through the year |
| 3 | 0.3.0 | Real blocks: a bare block per kind, swapped spot by spot as the leaf clock steps, Dynamic Trees season manager, note to Distant Horizons (plan rewritten below) | proof that Dynamic Trees accepts the bare block first, game tests of every row of "Cases thought through", long run |
| 4 | 0.4.0 | Finish: falling leaf particles, snow on crowns, fruit, `/foliage restore`, measurements, helper review | measurements file, review findings fixed |
| 5 | later | Far tree colors in Distant Horizons, falling tree, cherry blossom, leaf litter | each on the owner's word |

### Stage 1 parts, in build order

1. Project skeleton (Gradle, mods.toml, licence, README, changelog).
2. `util/PosHash`: fixed numbers per position (variant, scatter).
3. `shape/Surround`: what the six neighbors of a leaf block are (open
   sides, nearest wood, heart of the cluster, something lying on top).
4. `shape/Recipe` and `ShapeRules`: which pieces a position gets per
   shape. Pure code on ints, testable without a game.
5. `client/model/QuadMaker`: pieces to `BakedQuad`s (outer face, inner plane,
   card, small cube, twig cross).
6. `client/sprite`: sprite source `foliage:leaf_parts` that makes the round
   card picture from whatever leaf texture is loaded (resource packs work).
7. `client/model/LeafModel` and `LeafModels`: the wrapper and the wrapping at
   `ModelEvent.ModifyBakingResult` for every `DynamicLeavesBlock` whose model
   is a plain one-texture cube. Others are left alone and logged.
8. `client/FoliageClientConfig`: shape, fast leaves, the numbers of the shapes.
9. `gametest` and `dev/DevSelfTest`.

### Tricky spots, stage 1

- **Thread rule.** `getModelData` and `getQuads` run on chunk-building
  threads. Settings are one immutable record swapped as a whole; quad lists
  are built once per recipe into concurrent maps; no allocation per block
  beyond the `ModelData` (use a small table of prebuilt `ModelData` per
  recipe).
- **Hidden faces live in the model**, not in `skipRendering`: the wrapper
  answers `side != null` with nothing and puts the open outer faces into the
  unculled list. That works the same in the game's own chunk builder and in
  Sodium.
- **Render types.** Stay in the leaves' own types. In the solid pass (fast
  graphics) hand out outer cube faces only.
- **Seasons 0.3.0 still thins leaf textures** on the model it wrapped. Until
  stage 2 the wrapper steps aside (passes through) whenever Seasons shows any
  bareness, so autumn, winter and spring look as they do today.
- **Cards must never sit at 45 degrees** through a block (z-fighting with
  neighbors' cards); 22.5 degrees plus quarter turns by position.
- **Budget:** no leaf block is drawn from more than `maxFaces` faces
  (setting, default 6, the plain cube's count). Pieces are added in a fixed
  order per shape and left out when they would go over. Measured on real
  trees by the game tests and written to the log.
- Other mods' item and falling-block rendering call `getQuads` without our
  data: pass through to the original model.

### Stage 1 as built (2026-11-27)

Two things changed against the look prototype, both because of numbers
measured on real trees in the game tests:

- **A limit per block.** Without one, the shapes needed 5.1 to 5.8 faces per
  leaf block on oak, birch and dark oak, but 8.3 to 9.1 on spruce and 6.6 to
  7.8 on acacia, more than the plain cube's 6: thin crowns have nearly all
  their leaf blocks at the edge. With the limit at 6: 3.5 to 4.3 on full
  crowns, 5.3 to 5.6 on spruce, and never over 6 for any block. The limit is
  a setting (6 to 12); 12 gives the prototype's look back.
- **Clumps sit on their branch.** A clump is moved against the side its
  block hangs on to (wood, else the heart of the crown, else a covered
  side), and its face against a covered side is left out: 5 or 6 faces, no
  twig needed to keep it from floating. Where the limit allows a twig
  (from 9 or 10), the clump sits at the far end of its block instead and
  the twig carries it: a twig under a clump that rests on its branch stuck
  out of the leaves on the far side like antlers (seen in the first self
  test pictures).

Known and left as it is: a full leaf block next to a clump draws no face
toward it (it would have to read the clump's own neighbors to know). The
inner plane and card fill the gap.

### Tricky spots, stage 2

- The look rule must give the same answer on the server and on every client:
  integer arithmetic only, inputs are the calendar step, the block state and
  the position. "Has winter here" on the client comes from the biome at the
  position, not from the smoothed tint, for everything the server also
  decides (collision, swap); the drawn look may use the smoothed value.
- The leaf clock rides on Seasons' color steps (4 per day). Seasons 0.4.0
  gets: a step listener that can demand a redraw, a way to leave given leaf
  blocks to another mod, a public maker for coverage-keeping sprites.
- Face hiding between two leaf blocks depends on both looks: a full block next
  to one drawn bare keeps that face.
- Never the translucent render type.

### Stage 2 as built (2026-11-27)

The rule (`season/LeafClock`): the year is cut into steps (4 a day). Each
kind of leaves has a start day inside the window of fall or return; each
tree is shifted by smooth noise over its position (trees close together go
together); each leaf spot has its turn inside its tree's days, by its
Dynamic Trees depth and a fixed scatter. A spot's stage is a pure function
of step, kind, position and depth. Nothing is stored.

What a spot is drawn from at each stage (`shape/ShapeRules`), for the
shapes other than plain:

| Stage | Pieces |
| --- | --- |
| Full, thin, thinner | The shape's own pieces, with the stage's picture. |
| Last leaves | On a twig spot: the twig and one upright plane along it. Else two upright planes. |
| Bare | On a twig spot: the twig. Else nothing. |
| Buds | The same, with the picture of the twig with buds. |
| First leaves | On a twig spot: bud twig and one plane. Else a small clump against the support. |
| Half grown | A larger clump against the support, else outer faces and planes. |

Decided while building, each for a reason seen in numbers or pictures:

- **The plain shape stays cubes.** With `shape = PLAIN` every stage with
  leaves is the whole cube with the stage's picture, and leaf blocks never
  hide each other's faces. It is the unmodded look through the year, for
  players who want exactly that.
- **Twig spots.** Which leaf spots of a bare tree carry a twig is a setting
  (`winterTwigs`): none, the spots next to wood (default, about a quarter
  of the leaf blocks), or also spots deep in the crown.
- **Inside the crown.** A leaf block whose six neighbors are all leaves or
  solid blocks is buried in summer and costs nothing. While leaves fall or
  grow its neighbors open up and it would be drawn in full: the costliest
  days of the year were then days 4 to 6 of spring with 5.6 faces per leaf
  block. Such a block now draws outer faces only while in leaf, one upright
  plane as last or first leaves and two when half grown: 5.1 to 5.3 on the
  costliest day.
- **A leaf block covers its neighbors only while full or in its first
  thinning.** From the second thinning on the neighbor's face toward it is
  drawn.
- **Fast graphics.** Leaves that cannot be see-through are drawn as the
  outside of the crown while full, thinning or half grown, and not at all
  as last or first leaves.
- **Whose rules.** The rules of the world are the server's and are sent to
  each player. A client that is sent none (a server without Foliage) uses
  its own file instead of showing no year at all.
- **Seasons 0.4.0** got four calls instead of a step listener:
  `SeasonsApi.hasWinter`, `SeasonsClientApi.requestRedraw`,
  `withoutBareLeaves` and `leafSprite`. Foliage counts its own steps
  (`stepsPerDay`), so it does not depend on how often Seasons changes its
  colors.

Known and left for stage 3: the leaf blocks of a bare tree are still in
the world (snow, shade, collision), and because they still dim the light,
leaves left deep inside a half-bare crown are drawn too dark (see "Tricky
spots, stage 3"). Callers without the model's data
(items, falling blocks, Dynamic Trees' falling tree) get the full leaf cube
whatever the season.

### Stage 3 plan (rewritten 2026-11-27 after stage 2)

**What changed against the first plan, and why.** The first plan swapped a
whole tree once per transition, at night, and kept a mark per chunk. The
stage 2 pictures showed that this is not enough: a leaf block that is only
drawn bare still dims the light by one and darkens its neighbors' corners
like a full block. The last leaves of autumn and the first of spring, deep
in a crown whose outer blocks are drawn bare, came out nearly black, and the
ground under a bare tree stayed in shade. So the real block now follows its
own spot, step by step.

**The rule.** A leaf spot of a kind that sheds is a **bare block** (the
placeholder) while its stage is last leaves, bare, buds or first leaves,
and its **leaf block** while it is full, thin, thinner or half grown. Which
of the two is right is a pure function of the date, the kind, the place and
the Dynamic Trees depth: the same rule the look uses. Nothing is stored. In
total every leaf block still changes twice a year.

**The look does not depend on which of the two blocks is there.** Both are
drawn by the same leaf model from the same rule, so a block that has not
been swapped yet looks right already. The swap only changes light,
collision, what snow and rain do, and what Dynamic Trees and other mods
see.

Parts, in build order:

1. `block/BareLeavesProperties` and `block/BareLeavesBlock`: a leaf kind
   type `foliage:bare` for Dynamic Trees and its block. One per kind that
   sheds, as tree pack files `trees/foliage/leaves_properties/bare_oak.json`,
   `bare_birch.json`, `bare_dark_oak.json` (family and cell kit of the
   kind it stands in for). The block: a `DynamicLeavesBlock` without
   collision, without outline, without forced solidity (rain and snow fall
   through, nothing rests on it), light dimming 0, shade brightness 1,
   never ticks at random, can be replaced like air, does not burn, drops
   nothing, slows nobody. It keeps the Dynamic Trees depth of the leaf it
   replaced.
2. `block/Twins`: which bare block belongs to which leaf block, both ways.
   The leaf clock gives a bare block the schedule of its leaf.
3. Models: the bare blocks' block states point at the same model as their
   leaves, so the leaf model wraps them too. A bare block asked without the
   model's data (Dynamic Trees' falling tree) draws nothing.
4. `world/LeafWalker`: on the server, after each step of the leaf clock and
   whenever a chunk starts to tick, brings chunks up to date. Per chunk:
   skip sections whose palette has none of our blocks (outside the two
   windows: none of the wrong kind), else look at each block, work out the
   stage and swap if needed. A few chunks a tick, nearest to players first,
   with limits on chunks, changed blocks and time per tick (settings).
5. Quiet write: `LevelChunk.setBlockState(pos, state, false)`, then
   `ServerChunkCache.blockChanged(pos)` and `PathTypeCache.invalidate(pos)`.
   Never `Level.setBlock`, never neighbor or shape updates. Snow lying on a
   leaf that becomes bare goes with it.
6. Dynamic Trees fitting: species accept their bare blocks
   (`addValidLeafBlocks`); a season manager so that trees do not grow in
   winter; nothing drops from bare blocks when a tree is cut.
7. Distant Horizons: far crowns stay full (the API event that stores the
   leaf instead of the bare block), only if Distant Horizons is there.
8. Settings (`foliage-common.toml`, section `bareBranches`): on or off,
   chunks per tick, changes per tick, milliseconds per tick.
9. Game tests and self test (below).

### Tricky spots, stage 3

- **Only chunks that tick.** `ServerChunkCache.blockChanged` sends a change
  to the players only for a ticking chunk. A change made in a chunk that is
  loaded but does not tick would never reach the clients, and their copy of
  the world would differ from the server's for good. The walker asks
  `isPositionTicking` first and takes a chunk up again when it starts to
  tick. Land that is in view but too far to tick keeps its old light until
  someone comes near; its look is right all the same.
- **Dynamic Trees must see leaves.** A bare block is a `DynamicLeavesBlock`
  of the same family: it holds up its twig (`branchSupport`), passes water
  to the leaves beyond it (`getHydrationCell` with the kind's own cell kit,
  or the leaves behind a bare spot dry out and are removed), and no leaf
  grows into it (`isLocationSuitableForNewLeaves` refuses any
  `DynamicLeavesBlock`). To prove in a game test before anything else:
  many tree updates on a bare tree leave every twig and every bare block in
  place, and hydration numbers do not change.
- **Leaves that Dynamic Trees places in the bare months** (growth, a
  sapling becoming a tree, hydration updates that set the leaf state anew)
  are real leaves at first. The walker makes them bare at its next pass;
  outside the two windows it still passes over every ticking chunk at each
  step, where it only has to look at palettes.
- `DynamicLeavesBlock.getShadeBrightness` is fixed at 0.2 and
  `entityInside` slows whoever is in the block: both overridden.
- Leaves are water-loggable: water that reaches a bare block water-logs it
  and does not wash it away. Kept; the leaf it turns back into is
  water-logged too and Dynamic Trees deals with it as before.
- Clients redo light on their render thread with no budget: the limit on
  changed blocks per tick is what protects them, also after a time jump.
- With the leaf calendar switched off, or a kind taken off the list, the
  walker turns bare blocks back into leaves as chunks come to tick. That is
  also the way out before the mod is removed (`/foliage restore` in stage 4
  for land nobody visits).
- The leaf block's default state must stay a full leaf (Distant Horizons
  falls back to it for unknown stored states).
- The leaf and its bare block must agree on "is there a winter here": both
  sides read the biome without the game's fuzzy borders
  (`getNoiseBiome`), the server from the chunk itself.

## Tests

- **Game tests** (`./gradlew runGameTestServer`): the rules as pure checks,
  and in the world with real Dynamic Trees trees planted by command. Nothing
  may depend on the test's random position.
- **Self test** (`./gradlew runClient -Pfoliage.selfTest=true` under xvfb,
  Sodium in `run/mods`): plants a fixed scene, photographs it from fixed
  views, counts faces per leaf block and checks pictures in numbers.
- **Measurements** (stage 4): server time per tick in a swap, light work,
  faces and memory per season, redraw at a clock step, against the same
  forest without the mod.
- **Review:** a separate helper pass against this plan and the design doc
  when stage 4 is done; one pass, findings fixed, tests run again.
