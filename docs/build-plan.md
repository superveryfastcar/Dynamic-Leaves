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
  the leaf clock steps, with nothing stored (see "Stage 3 as built").
- A spot in full, thinning or half-grown leaf is a real leaf block. A spot
  that shows its last or first few leaves, buds, a twig or nothing is a
  bare block: no collision, no outline, no shade.
- The placeholder is a second leaf kind of the same Dynamic Trees family, so
  Dynamic Trees sees leaves (no rot, no regrowth) while the game sees
  something close to air.
- No mixins into Dynamic Trees or Sodium.

## Stages

| # | Version | What | Proof |
| --- | --- | --- | --- |
| 1 | 0.1.0 | Shapes: sprite source for leaf cards, shape rules, leaf model, wrapping of Dynamic Trees leaves, client settings | unit-style game tests of the rules, self test with pictures and face counts, with Sodium |
| 2 | 0.2.0 | Leaf calendar: the look rule, thinned and small leaf sprites, twigs and buds, stages in the model, Seasons 0.4.0 API | game tests of the rule day by day, self test through the year |
| 3 | 0.3.0 | Real blocks: a bare block per kind of leaves, swapped spot by spot as the leaf clock steps (as built below) | game tests that Dynamic Trees holds a bare crown and of every case below, self test through the year in a real client |
| 4 | 0.4.0 | Finish: falling leaf particles, snow on bare crowns, season source for Dynamic Trees (no growth in winter, fruit in its season), `/foliage restore`, Distant Horizons with bare blocks, measurements in a real forest, helper review | measurements file, review findings fixed |
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

### Stage 3 as built (2026-11-27)

**Why the plan changed after stage 2.** The first plan swapped a whole tree
once per transition, at night, and kept a mark per chunk. The stage 2
pictures showed that this is not enough: a leaf block that is only drawn
bare still dims the light by one and darkens its neighbors' corners like a
full block. The last leaves of autumn and the first of spring, deep in a
crown whose outer blocks are drawn bare, came out nearly black, and the
ground under a bare tree stayed in shade. So the real block follows its own
spot, step by step.

**The rule** (`world/SpotRule`, `LeafStage.bareBlock()`). A leaf spot of a
kind that sheds holds its **bare block** while its stage is last leaves,
bare, buds or first leaves, and its **leaf block** while it is full, thin,
thinner or half grown. Which of the two is right is a pure function of the
date, the kind, the place (is there a winter in this biome) and the Dynamic
Trees depth: the same rule the look uses. Nothing is stored. In total every
leaf block still changes twice a year.

**The look does not depend on which of the two blocks is there.** Both are
drawn by the same leaf model from the same rule, so a block that has not
been swapped yet looks right already. The swap only changes light,
collision, what snow and rain do, and what Dynamic Trees and other mods
see.

What was built, and where it differs from the plan:

- **Bare kinds are made in code, not by tree pack files** (`block/Twins`).
  Every kind of leaves of Dynamic Trees and its add-ons of the plain or
  the scruffy sort gets one, whether or not it sheds by today's settings:
  12 for Dynamic Trees alone. So the owner can put any kind on the list of
  kinds that shed without a new file, and an add-on's trees work without
  one. Names: `foliage:bare_<path>`, for other mods
  `foliage:bare_<namespace>_<path>`. They are written into worlds, so the
  naming rule must never change. Kinds of another sort (palm
  fronds, for one) get none and never shed.
- **A bare block passes every question on to its leaf kind**
  (`block/BareLeavesProperties`): family, cell kit, flammability, light.
  For Dynamic Trees it is that kind of leaf in every way but the block.
- **Bare blocks burn like wood.** The plan said "does not burn". Fire in
  the game only takes hold in an air block next to something that burns,
  and a bare block is not air: a crown of blocks that cannot burn would
  shield its twigs and branches from fire for good. So fire can eat a bare
  block, as slowly as a log (5), and does not start in the air next to it
  (0, as for the thinnest twig of Dynamic Trees): a bare tree does not go
  up in flames as a tree in leaf does, which is what the design says. Both
  numbers are settings (`twigsBurn`, `twigsSpreadFire`). Dynamic Trees
  answers the game's two fire questions for its leaves with the numbers
  crossed over (30 where the kind says 60); the bare block answers each
  with its own.
- **Water does not flow into a bare block**, as it does not flow into
  leaves. The design said water treats it as air. Kept as it is on
  purpose: water that a crown holds back in summer would break through
  every winter and change the land, and a leaf spot that water has taken
  is lost to the tree.
- **Block state files come from memory** (`client/BareBlockStates`, a
  resource pack made at start-up), one per bare block, each pointing at a
  model that draws nothing. `LeafModels` then puts the leaf's own wrapped
  model into the bare block's slots. Asked without the model's data (the
  falling tree of Dynamic Trees), a bare block draws nothing.
- **The walker** (`world/LeafWalker`), one per world on the server, looks
  at chunks and makes their blocks agree with the rule:
  - a round over every chunk in reach at each step of the leaf calendar,
    the chunks nearest to a player first;
  - every chunk that comes into reach;
  - first of all, chunks in which a bare block noticed a wrong leaf block
    next to it (`BareLeavesBlock.updateShape`): a twig that grew in winter.

  A section is skipped by its palette of block kinds and of biomes alone
  when nothing in it can be wrong. Limits per tick are settings
  (`foliage-common.toml`, section `bareBranches`): `bareBlocks` (on or
  off), `chunksPerTick` 64, `changesPerTick` 300, `millisPerTick` 1.0.
- **Quiet write:** `LevelChunk.setBlockState(pos, state, false)`, then
  `ChunkHolder.blockChanged(pos)` and `PathTypeCache.invalidate(pos)`.
  Never `Level.setBlock`, no neighbor or shape updates. Snow lying on a
  leaf that goes bare goes with it; anything else that hung on to it (a
  vine) is told so and lets go.
- **Dynamic Trees fitting.** Species accept their bare blocks
  (`Twins.fitSpecies`, at server start and when tags reload). Whenever
  Dynamic Trees is about to put a leaf down through a bare block or to
  change a supply number, it asks the block which state to use
  (`getLeavesBlockStateForPlacement`); the answer is what the rule wants
  there (`LeafWalker.fit`). So a tree that grows in winter grows bare.
- **Moved to stage 4: the season source for Dynamic Trees** (no growth in
  winter, seeds in autumn, fruit in its season). The plan had it here so
  that winter growth would not fill a bare crown with leaf blocks. That is
  solved without it, so it goes with the fruit, where it belongs.
- **Moved to stage 4: Distant Horizons.** Not tested with bare blocks yet.

**Proof** (game tests in `gametest/BareBranchGameTests`, 11 tests; all 39
of the mod pass):

| Question | Answer, with the number measured |
| --- | --- |
| Does Dynamic Trees hold a bare crown? | Yes. Branch support summed over every branch of a big oak is the same bare as in leaf, and no bare block goes under repeated supply checks. |
| Does a year leave the tree as it was? | Yes, block for block and supply number for supply number (290 leaf blocks), and the light is the same again. |
| Is the ground under a bare tree bright? | Sky light 15 on every ground column under the crown, against shade under most of it in leaf. Rain reaches the ground; the game's height map does not see bare blocks. |
| Does the look jump when a block is swapped? | No. Through autumn and spring, every two steps, on an oak and a birch, each spot is drawn from the same pieces before and after its swap, and each spot is swapped exactly once each way. |
| What if a tree grows in winter? | A young oak, 120 growth pulses in midwinter: from 104 to 237 crown spots, 133 of them put down as leaf blocks by Dynamic Trees, all bare three ticks later without a round of the walker. |
| What if a bare tree is cut down? | The falling tree is made of bare blocks, drops nothing for them, and what is left of the crown decays. |
| Do the limits hold? | With 25 swaps per tick the walker took 12 ticks for 286 blocks, never more than 25 in one, and 12 ticks to bring them back with bare blocks switched off. |
| Where there is no winter? | A tree half in a desert keeps leaves on that half and goes bare on the other. |
| A bare block where none belongs? | Becomes its leaf at the next look (kind taken off the list, leaf calendar off, mod about to be removed). |

**Self test** (a real client with Sodium 0.8.13, the scene of 20,062 leaf
blocks of which 19,324 shed, the whole year at 4 steps a day; 508 checks,
all passed):

- All 12 bare blocks, in all 336 of their states, are drawn by the model of
  their leaves.
- Every one of the 19,324 leaf blocks was made bare in autumn and given its
  leaves back in spring, each once. At every look the player's game held
  the same block as the server at every spot of the oak grove (1,603
  spots), and every spot held the block its stage asks for.
- Light: under the grove 336 of 336 ground columns are in shade in summer
  and none in winter; sky light at the darkest bare block is 15 in winter,
  and never under 9 while the crown thins. The leaves still on a
  half-bare crown stand in sky light 13 to 14.
- The server's cost for the whole year: 405 ms in 20 rounds, on a machine
  that draws the game in software at the same time (so these are upper
  numbers). A round in summer, 617 to 892 chunks at a glance: 3 ms. The
  largest step, 7,952 swaps: 35 ms, spread over ticks of at most 1 ms. A
  round in winter or mid-fall, when 61 sections are looked at block by
  block: 10 to 15 ms, which is 0.2 ms per section.

That last number is the one to bring down in stage 4. A section that has
been swapped keeps the names of its leaf blocks in its palette until it is
next read from disk, so in winter the walker looks at it block by block at
every step and finds nothing. In a real forest in full view that would be
about a quarter of a second of server time per step, at 1 ms a tick.
Counting the palette's entries first would tell at a twentieth of the
cost that none of them is left.

### Tricky spots, stage 3 (as they turned out)

- **When to register the bare blocks.** NeoForge posts the registration
  event once per priority across all mods, not once per mod. Dynamic Trees
  loads its tree packs at normal priority and registers its leaf blocks at
  lowest. Foliage therefore registers at lowest priority too, after
  Dynamic Trees in mod order: only then do the kinds exist. At normal
  priority it found 16 kinds and no block.
- **Which chunks the walker may touch.** `ChunkHolder.blockChanged` sends
  a change to the players only for a chunk the server holds at ticket
  level 32 or lower (`ChunkTicketLevelUpdatedEvent`). That covers the
  whole view distance, so land in view gets its swap and its light. A
  chunk that is only kept in memory is left alone until it comes into
  reach; otherwise a player's copy of it could differ from the server's
  for good. (The plan's `isPositionTicking` was too narrow.)
- **Dynamic Trees does not send every supply number.** When it changes the
  `distance` of a leaf block and the look does not change with it, it
  writes without telling the players. A player's game can so hold an old
  number for a few leaf blocks, and draw them one step of the leaf
  calendar off until the block is next swapped or sent. Seen in the self
  test: 6 of 1,610 spots. Left as it is (no mixin into Dynamic Trees); a
  bare block does tell the players (`appearanceChangesWithHydro`), because
  there the change can swap the block.
- **A crown in leaf never comes to rest** under Dynamic Trees' ageing:
  leaves at the rim grow and die at random. Tests that compare a tree
  before and after settle the supply numbers with `updateHydro` alone.
- **Growth in winter comes as leaf blocks first.** A twig that grows into
  open air places its leaves through the species' leaf block, not through
  a bare one. The bare neighbor notices, and the walker puts the spot right
  in the next tick, ahead of its queue.
- **Animals are not asked to think again** when a block is swapped, as the
  game does for a block placed by hand: with hundreds of swaps in a tick
  that would cost more than everything else. What each spot is for an
  animal that plans a new way is kept up to date (open air).
- `DynamicLeavesBlock.getShadeBrightness` is fixed at 0.2 and
  `entityInside` slows whoever is in the block: both overridden.
- Leaves are water-loggable: water that reaches a bare block water-logs it
  and does not wash it away. Flowing water does not enter it. The leaf it
  turns back into is water-logged too.
- Clients redo light on their render thread with no budget: the limit on
  changed blocks per tick is what protects them, also after a time jump.
- **The game test server does not wait between ticks.** A test that reads
  light waits for the light thread
  (`ThreadedLevelLightEngine.waitForPendingTasks`), not for a number of
  ticks.
- With the leaf calendar switched off, `bareBlocks = false`, or a kind
  taken off the list, the walker turns bare blocks back into leaves as
  chunks come into reach. That is also the way out before the mod is
  removed (README, "Taking the mod out of a world"; `/foliage restore` in
  stage 4 for land nobody visits).
- The leaf and its bare block must agree on "is there a winter here": both
  sides read the biome without the game's fuzzy borders
  (`getNoiseBiome`), the server from the chunk itself.
- **Every player needs the mod** from 0.3.0 on, to join a server that has
  it: the bare blocks are blocks of the world.

Open after stage 3, for stage 4:

- Snow cannot lie where a bare block is, so a bare crown stays without
  snow. Snow on bare crowns needs an answer of its own.
- The cost of a round over a forest that is already bare (see the self
  test numbers above): count the palette before looking block by block.
- Throughput when a whole forest must be swapped at once (a player who
  arrives in a winter forest that was last seen in summer): about 1,500
  leaf blocks per forest chunk against 300 swaps per tick. The look is
  right at once and the nearest chunks go first; to be measured in a real
  forest and tuned.
- Chunks that are made in winter are made in leaf and swapped when they
  come into reach. They could be made bare before they are first sent.
- Distant Horizons with bare blocks.
- The season source for Dynamic Trees that the design promises: no growth
  in winter unless a setting allows it, seeds in autumn, fruit in its
  season. It was left out of stage 3 because the bare crown no longer
  needs it; it is built in stage 4 with the fruit.

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
