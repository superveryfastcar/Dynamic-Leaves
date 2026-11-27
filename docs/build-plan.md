# Dynamic Leaves: build plan

Written 2026-11-27 before any code. The builder follows it and the reviewer
checks the code against it. Design for the owner to read:
https://claude.ai/artifact/AXZ8oBJWzArmH867JfoAE8

Working name Dynamic Leaves, mod id `dynamicleaves`, package
`io.github.superveryfastcar.dynamicleaves`, MIT, author coolcritter22, NeoForge
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

Owner's words after seeing the pictures of 0.4.0 (2026-11-27, fixed, built
in 0.5.0):

7. The winter twig must fit Dynamic Trees and "quite literally sit inside
   the leaf block. It should be 3d, just like the other branches." "Think
   of the twig texture like mini branches. They can branch in several
   directions within a block. Maybe one protruding one with 3 smaller
   protruding from it." The step from autumn 12 to 13 "looks awful".
8. "The leaf thinning could be a mix of leaf blocks getting smaller, getting
   to the state theyre in at autumn 11, maybe slightly fuller, and
   disappearing."
9. "There should probably also be some sort of effect for it if the player
   is looking rather than just instant replacement maybe like block
   breaking visual effect or something idk. If its not too performance
   costly."

On decision cards (2026-11-27, open; built on Claude's pick until answered):

- Winter leaf spot: placeholder block with twigs near branches (pick),
  placeholder with more twigs, or plain air.
- Leaf shape: shell, bushy, tufts, mixed (pick). All four are built as a
  player setting, so the card only sets the default.
- Name: Dynamic Leaves (pick), Boughs, Deciduous, Branches.

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
| 4 | 0.4.0 | Finish: falling leaf particles, snow on bare crowns, season source for Dynamic Trees (no growth in winter, fruit in its season), `/dynamicleaves restore`, Distant Horizons with bare blocks, measurements in a real forest, helper review | measurements file, review findings fixed |
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
6. `client/sprite`: sprite source `dynamicleaves:leaf_parts` that makes the round
   card picture from whatever leaf texture is loaded (resource packs work).
7. `client/model/LeafModel` and `LeafModels`: the wrapper and the wrapping at
   `ModelEvent.ModifyBakingResult` for every `DynamicLeavesBlock` whose model
   is a plain one-texture cube. Others are left alone and logged.
8. `client/DynamicLeavesClientConfig`: shape, fast leaves, the numbers of the shapes.
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
  each player. A client that is sent none (a server without Dynamic Leaves) uses
  its own file instead of showing no year at all.
- **Seasons 0.4.0** got four calls instead of a step listener:
  `SeasonsApi.hasWinter`, `SeasonsClientApi.requestRedraw`,
  `withoutBareLeaves` and `leafSprite`. Dynamic Leaves counts its own steps
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
  one. Names: `dynamicleaves:bare_<path>`, for other mods
  `dynamicleaves:bare_<namespace>_<path>`. They are written into worlds, so the
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
  (`dynamicleaves-common.toml`, section `bareBranches`): `bareBlocks` (on or
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
  lowest. Dynamic Leaves therefore registers at lowest priority too, after
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
  removed (README, "Taking the mod out of a world"; `/dynamicleaves restore` in
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

### Stage 4 plan (2026-11-27)

Checked against the design doc's "Cases thought through", "Settings",
"Fitting into Dynamic Trees" and "Other mods" before any code.

Parts, in build order. All of them are written by the largest model; the
complicated ones are marked.

| # | Part | Where | Complicated |
|---|------|-------|-------------|
| 1 | **Walker cost in winter.** Before a section is looked at block by block, count the blocks its list names (`PalettedContainer.count`): a section whose bare blocks are all right and that holds no leaf that sheds is done by the count alone. | `world/LeafWalker.look` | no |
| 2 | **Trees rest in winter.** Dynamic Leaves signs in as Dynamic Trees' season source (`SeasonCompatibilityHandler.registerSeasonManager("dynamicleaves", ...)`). The season comes from Calendar. Where Seasons says there is no winter, trees grow, drop seeds and bear fruit all year. Where there is one, Dynamic Trees' own curves apply (growth picks up through early spring and slows in late autumn, seeds mostly in autumn, fruit from spring to autumn), and in winter all three stand still unless the setting `growthInWinter` is on. | new `world/TreeSeasons`, `DynamicLeavesConfig`, `Dynamic Leaves` | yes: threads (worldgen asks from worker threads), the client asks too (tooltips), which source wins when Serene Seasons is installed |
| 3 | **Fruit.** Fruit that hangs under a leaf block that goes bare drops off as if picked. Fruit that appears under a bare block is taken away in the next tick. | `LeafWalker.settle`, `BareLeavesBlock.updateShape` | no |
| 4 | **Snow.** Snow that lay on a leaf that goes bare falls to whatever is below it in its column (the next leaf, a branch that can hold it, the ground) and lies there if snow can lie there and none lies there yet. Checked with Seasons' snowfall, its catch-up and Snow! Real Magic. | `LeafWalker.settle` | yes: order against Seasons' catch-up, heightmaps |
| 5 | **`/dynamicleaves restore`.** Switches `bareBlocks` off, then turns every bare block of the world back into its leaf: loaded land through the walker, land on disk chunk by chunk (only chunks whose saved data names a bare block are loaded), a few per tick, with a progress line. `/dynamicleaves restore status`, `/dynamicleaves restore stop`. `/dynamicleaves bare on` switches bare branches back on. | new `command/DynamicLeavesCommand`, new `world/Restore` | yes: reading region files, loading chunks without making new land, the server must stay responsive |
| 6 | **Falling leaves.** A particle of the mod's own (a small leaf, tinted in the tree's color of that day) that drifts down from leaf spots at the stages "thin", "thinner" and "a few last leaves". Made in each player's game from random spots near the camera; nothing is sent. Settings: on or off, amount, distance, most particles alive. Costs nothing outside the weeks leaves fall. | new `client/fall/*`, particle type, pictures, `DynamicLeavesClientConfig` | yes: the look |
| 7 | **Distant Horizons.** Where a bare block stands, Distant Horizons is handed the full leaf (`DhApiChunkProcessingEvent`), so far crowns stay whole whatever the season. Only if Distant Horizons is installed; compiled against its API jar only. | new `compat/DistantHorizonsLink` | yes: must not load without DH, runs on DH's threads |
| 8 | **Measurements.** The self test's forest at once from summer to winter with the default limits (ticks, time per tick, swaps per tick), a round over the bare forest before and after part 1, particles alive at the peak of the fall, written to `docs/measurements.md`. | `dev/DevSelfTest` | no |
| 9 | README, changelog, "Stage 4 as built", design doc, review by a helper, fixes, release. | | |

Decisions taken here that the design doc does not spell out (to name in the
report):

- In winter not only growth stands still but also seeds and new fruit: a
  tree that rests, rests. One setting (`growthInWinter`) for all three.
- The curves outside winter are Dynamic Trees' own (the ones it uses with
  any season mod), so tree packs that shift a species' fruit season keep
  working.
- Snow on a leaf that goes bare falls down instead of vanishing (the design
  doc says "taken away"). Without this, land that was last seen in autumn
  and is caught up in winter gets its snow on the crowns first, loses it
  with the leaves and stands on bare ground in a white forest.
- Snow does not lie on a bare crown, as the design doc says. Snow drawn on
  bare branches would be a new feature with its own state and is offered,
  not built.
- Chunks made in winter stay as they are (made in leaf, swapped when they
  come into reach): the look is right from the first frame, and the snow
  finds its way down by the rule above.

Tricky spots:

- **Threads.** Dynamic Trees asks the season source while it makes new
  land, on worker threads. The source reads the Calendar (safe from any
  thread) and the biome without asking the server for a chunk
  (`getUncachedNoiseBiome` off the server thread).
- **Config timing.** Dynamic Trees picks its season source whenever one of
  its settings files is read. The source must be signed in before that
  (mod constructor) and picked again once all mods are set up.
- **Fruit under bare blocks** is supported by Dynamic Trees' own rule (the
  bare block is a leaf to it), so it has to be taken away by us.
- **Restore** must not make new land: a chunk is loaded on its own, without
  its neighbors being brought to "ticking".
- **Particles** must not cost anything in summer and winter: the sampler
  returns at once unless the leaf calendar is in its fall window.

Tests: a game test per row of the table above that has a rule (walker
count skip, the three factors per season and place, fruit drop, snow
fall-through and landing, restore on loaded land and on a saved chunk's
data, the Distant Horizons swap as a pure function), and the self test for
the particles, the measurements and one run each with Snow! Real Magic and
Distant Horizons in `run/mods`.

### Stage 4 as built (2026-11-27)

Every part of the table was built. Where it differs from the plan, the
reason is given.

**1. The walker's cost in winter** (`world/LeafWalker`). Before a section
is searched block by block, its blocks are counted in one pass over the
section's own numbers (`PalettedContainer.count`, which walks the palette
and the bit array, not 4,096 block states): how many bare blocks it holds,
how many of those are at the wrong block for this date, and how many leaf
blocks of a kind that sheds. A section whose count says that nothing is
wrong is done at that. In winter, when nothing changes for weeks, that is
what a round over a bare forest costs. The counters of the walker got
names (`TO_BARE`, `TO_LEAF`, `CHUNKS_LOOKED`, `SECTIONS_SEARCHED`,
`ROUNDS`, `NANOS`, `SECTIONS_COUNTED`, `SNOW_LANDED`, `FRUIT_DROPPED`),
because the self test reads them by index.

**2. Trees rest in winter** (new `world/TreeSeasons`). Dynamic Leaves offers
itself to Dynamic Trees as its season source
(`SeasonCompatibilityHandler.registerSeasonManager("dynamicleaves", ...)`) and
sets the manager when a server starts, because Dynamic Trees picks its
source whenever it reads its own settings, which can be after Dynamic Leaves has
started. Whether it listens is its own choice
(`preferredSeasonMod`: `*` or `dynamicleaves`).

The source keeps nothing at all: Dynamic Trees asks it from worldgen
worker threads and in the client (the tooltip of a seed), so every answer
is worked out from the date (Calendar) and the place. Where Seasons says
there is no winter it answers with Dynamic Trees' own "no climate zone",
which makes Dynamic Trees' own code give a factor of 1 all year: a hot
biome needs no special case here. Where there is a winter, the three
factors (growth, seeds, fruit) are Dynamic Trees' own curves of the year,
so a tree pack that shifts a species' fruit season keeps working; and in
winter all three are 0 unless `growthInWinter` is on.

**3. Fruit** (`LeafWalker.settle`, `block/BareLeavesBlock.updateShape`).
Fruit hangs under a leaf block and Dynamic Trees lets any leaf block hold
it up, bare ones too, so it does not fall by itself. The walker drops it
when the spot above it goes bare (`Growable.drop`, which is what the mod
itself does when fruit is picked), and a fruit that Dynamic Trees puts
under a block that is already bare is noticed by that block and dropped in
the next tick.

**4. Snow** (`LeafWalker.land`). Snow that lay on a leaf that goes bare is
moved down to the first block that can hold it instead of being taken away
(a decision not in the design doc): land that is caught up in winter would
otherwise get its snow on the crowns, lose it with the leaves, and stand
as a white forest on bare ground.

**5. `/dynamicleaves restore`** (new `world/Restore`, new
`command/DynamicLeavesCommand`). The plan said to load only chunks whose saved
data names a bare block. Finding that out means reading and decompressing
every chunk, which costs about as much as loading it, so instead only the
4,096-byte header of each region file is read: it gives the list of chunks
that were ever written, and those are swept, a few per tick (4 by default,
1 to 64, within 20 ms of a tick), with a progress line every ten seconds.
Land that is loaded needs no pass of its own, because the command switches
`bareBlocks` off first and that starts a walker round over everything in
reach. The sweep must not make new land: a chunk is loaded on its own; at
the edge of written land the game may still generate a neighbour to light
it, which is its own doing and gives the same land a visit would.

**6. The leaves in the air** (new `client/fall/*`). Not a particle type of
the mod's own, as the plan said: a particle type is an entry in a registry
that a server and a client have to agree on, and these leaves are each
player's own business. The client makes them itself and hands them to the
game's particle engine.

The pictures then had to move. The game only lets a mod's pictures into
the particle atlas through such a type: the particle atlas is not among the
atlases the model manager keeps, and asking it for a picture takes the
client down (found by the first run of the self test, which crashed at the
first leaf). So the four leaf pictures sit among the block pictures and are
drawn the way the game draws the crumbs of a broken block
(`ParticleRenderType.TERRAIN_SHEET`): cut out, with the block pictures
bound.

A leaf starts under a leaf block that is losing its leaves right now, in
that tree's colour of the day, drifts down, swings in a widening circle,
turns, and lies still for a moment on the ground. The places are drawn at
random from the air around the player, as the game does for its own
weather: a forest costs no more than a single tree. Outside the days when
leaves fall nothing is looked at.

**7. Distant Horizons** (new `compat/DistantHorizons`,
`compat/DistantHorizonsLink`). Built as planned: the first class only asks
whether the mod is installed, the second, which names its classes, is
loaded by name and only then. It hands Distant Horizons the leaf block
wherever a bare block stands, as that mod builds its own picture of a
chunk, which covers every way into that picture. Compiled against an
API-only jar; nothing of it is in Dynamic Leaves' jar.

It cannot be tried out here: there is no Distant Horizons jar in this
container and the machine has no real graphics. The test is that a game
without the mod is untouched (`distantHorizonsSleepsWithoutTheMod`), and
the look of it on a real machine is open.

**8. Measurements**: `docs/measurements.md`.

**9. A flaky test found and fixed.** `stepByStepThroughAutumnAndSpring`
walked the year in steps of two and asked for at least four steps with the
crown partly bare. In spring the block comes back near the end of each
spot's own change, so the swaps fall within about two days: at two steps a
day, a plot where the scatter of the spots lines up gave three. It now
walks every step and asks the question of the swaps themselves (at least
four steps changed something, and no step swapped more than two thirds of
the crown), which does not depend on where in the world the test plot
lands. Seven runs in a row of the whole suite pass.

**10. What the review found.** One separate pass against this plan and the
design doc, in a fresh context, on the finished stage. Everything below is
fixed and the suite was run again (57 tests, five of them new).

Wrong behaviour:

- **`/dynamicleaves restore` could make new land.** A region file's header names
  the half-made chunks around the visited land as well as the finished
  ones, and asking the game for one of those in full makes it: caves,
  trees, light, and more half-made neighbours with it, so a sweep would go
  on making land for as long as it ran. Every chunk is now opened at the
  lowest step first (`ChunkStatus.EMPTY`), which only reads what is on
  disk, and only a chunk that was written as a finished one is asked for in
  full. No bare block can stand in a chunk that was never finished.
- **The sweep changed chunks under the players' feet without telling
  them.** `LeafWalker.sweep` wrote the blocks quietly but had no chunk
  holder to tell, so a player watching a tree the sweep reached saw the old
  crown until something else in that chunk changed. It now takes the holder
  from the walker's own list of chunks in reach, as a walker round does.
- **The world was not written before the sweep read the region files.** A
  chunk that is loaded but that nobody watches any more would hold bare
  blocks that no region header names, and would be written back with them
  after the sweep had said the mod could be taken out. Each world is now
  saved first.
- **Snow came down by the order the blocks were looked at.** It stopped at
  the first block below that was not air, even one that nothing rests on,
  so snow could vanish under a bare block. It now falls past everything
  that does not stop a body and comes to rest on the first block that can
  hold it.
- **The count of leaves in the air leaked when a player changed world.**
  The game throws every particle away at a portal without telling the
  particles, so the count kept leaves that were long gone and in the end
  allowed none at all. The count now belongs to a world, held weakly, and
  starts again when the world changes.
- **The biome was read two different ways.** `TreeSeasons` read it with the
  level's own cache, which on a worker thread makes that thread wait for
  the server's thread (`ServerChunkCache.getChunk`), exactly what the
  design says must not happen; and it read a different biome from the one
  the leaf blocks go by. Both now read the stored biome in cubes of 4 by 4
  by 4 blocks, and a worker thread of a server works it out from the
  world's shape instead.
- **`/dynamicleaves bare on` was allowed while a sweep ran**, which would have
  left the land behind the sweep leafy and the land ahead of it bare. It is
  refused now, and says what to do instead.
- **Chunks that were loaded but out of the walker's reach were missed** by
  a round, and the class notes said otherwise.
- **The sweep's last report was short** by the chunks of its final tick.
- Smaller: the date was read twice while working out the season, so a tree
  asked in the moment of a midnight that ends a season could get one
  season's number with the other's progress; `offer()` was not done again
  when `seasonSource` was switched on; the answer Distant Horizons gives to
  a sign-up was ignored, so a refused handler still counted as a link;
  Dynamic Leaves took Dynamic Trees' season source from another season mod without
  saying so; two comments and one setting's description did not match what
  the code does.

Taste:

- Fruit that has only just set is taken away quietly instead of broken: it
  is worth nothing, and breaking it gave a crack and a puff of crumbs for
  an empty hand.
- Where the sweep leaves the light a shade too bright is written down in
  `world/Restore`, with why it does not matter.

Tests the review found missing, now written: the region header read from a
region file written by hand (which is what decides whether the sweep makes
land, and the one thing a test world cannot show, having no land nobody has
visited); the whole way out as a player walks it (start, status, the
refusal of `bare on`, a second start, stop, and `bare on` again); fruit
appearing under a block that is already bare; snow falling past a bare
block. A fifth new test measures what the winter shortcut saves, on the
test server where nothing else is running: the same chunk looked at 700
times while its sections are searched and 700 times while they are only
counted (see `docs/measurements.md`). Three checks were tightened: the command test now counts the lines
each branch says instead of letting one branch fail, the "marked to be
written" check now marks the chunk as written first so that it can fail,
and the tests read the walker's counters by name.

A design decision of Claude's that is Lucas's to overrule: with
`preferredSeasonMod = "*"`, which is Dynamic Trees' default and means "the
first season mod that signed in", Dynamic Leaves takes the source even when
another season mod is installed, and says so in the log with the line to
set to pick the other one. Dynamic Trees' own meaning would leave it to the
order the mods happen to load in. Dynamic Leaves wins because the leaves of the
trees go by the Calendar date, and a tree that rested by another mod's
reckoning would stand bare in the wrong week.

### 0.5.0 as built (2026-11-27): mini branches, the shrinking fall, the burst

Three things Lucas asked for after the pictures of 0.4.0 (decisions 7 to 9).
All three are drawing only: the server, the bare block, the walker and the
leaf calendar are as in 0.4.0.

**Mini branches (`shape/MiniBranch`, `client/model/QuadMaker.box`).** The
flat twig pictures are gone, with their 24 PNG files, their atlas entries
and `tools/make_twigs.py`. A leaf spot of a bare tree that touches wood now
holds a mini branch: boxes along the axes in the bark of the tree's own
branch, which is how Dynamic Trees builds its branches (read in its
`BasicBranchBlockBakedModel`: a core box and sleeves to the block sides,
bark columns picked by radius). Nothing of its code is copied; the boxes
and the bark coordinates are Dynamic Leaves' own.

- The main twig is 2 by 2 pixels, the thickness of the thinnest branch of
  Dynamic Trees, and 9 to 12 pixels long. Up to three twigs of 1 by 1
  pixel leave it at different heights in different directions. Four ways
  of setting them times four quarter turns give sixteen mini branches,
  picked by the position's variant.
- Where it starts depends on the wood: at the block's side where a thin
  branch reaches it with a stub of its own (`FLUSH`; the question Dynamic
  Trees asks to decide about the stub, `getRadiusForConnection`, is asked
  here too, so the two always agree), seven pixels inside a thicker branch
  whose bark lies behind the side (`REACH`), and in the middle of the leaf
  spot it hangs on to for the "more twigs" setting (`DEEP`, a thinner one
  with two twigs).
- The bark comes from the model of the family's branch block (its particle
  picture), then the family's plain log, then oak log. A leaf kind with no
  bark gets no mini branches, and the log says so once.
- Faces: 5 for the main twig, 4 for each thinner one (its far end is one
  pixel and is left out), so 17 for a whole mini branch. All measures that
  could meet a leaf cube sit a quarter of a pixel off, so no two faces
  ever share a plane and nothing flickers.
- New settings: `twigForks` (0 to 3). `winterTwigs` and `twigDepth` keep
  their names and now choose mini branches.

**The shrinking fall (`shape/ShapeRules`: `mostly`, `few`, `leafless`).**
A leaf block no longer goes from a whole block of specks to a flat twig.

| Stage | A spot that touches wood | Another spot |
| --- | --- | --- |
| Thin | the pieces of full leaf, thinned picture | the same |
| Most leaves gone | a clump of 12 against the wood, the main twig showing | a clump of 12 against the spot it hangs on to |
| Last leaves | the mini branch, a tuft of 8 on the end of its main twig | a clump of 8 against the spot it hangs on to, or nothing if that spot has no leaves |
| Bare | the mini branch | nothing |
| Buds | the mini branch, a bud of 4 on the end of its main twig | nothing |
| First leaves | the mini branch, a tuft of 8 on its end | a clump of 8 against a spot in leaf, else nothing |
| Half grown | a clump of 12 against the wood | a clump of 12 against the spot it hangs on to |

- The second picture of autumn is fuller than it was (half of the leaves
  gone instead of two thirds), because getting smaller now says "fewer
  leaves" as well. The picture of the last leaves is only used on the rare
  spot that hangs on to nothing.
- A clump never grows while its leaves fall and never shrinks while they
  come back (checked for every surrounding in `piecesAtEveryStage`).
- `Surround` got one more bit: whether the leaf block toward the heart has
  no leaves to speak of. The answer comes from the same question that is
  asked about every leaf neighbor anyway (`LeafCover.leaves`), so it costs
  nothing more.
- New settings: `fallSize`, `lastSize`, `budSize`.

**The burst (`client/fall/LeafBursts`).** At a step of the leaf clock
during the fall, the chunk sections around the player are gone through,
nearest first, a few per tick. A leaf spot in a cone of 120 degrees in
front of the player (or within 4 blocks) whose stage moved on lets go of
`burst` leaves (default 3), the same particles as the leaves in the air,
under the same cap. A block-breaking effect was the other idea; it was not
taken because breaking particles are made per block by the game's own
code, show the block's whole texture as square crumbs, and cannot be
capped per player. Leaves that let go read as what is happening.

Claude's own decisions in 0.5.0, for Lucas to overrule:

- The mini branch is as thick as the thinnest branch of Dynamic Trees, so
  it reads as that branch going on. A thinner main twig would make the
  step from branch to mini branch visible.
- Every leaf spot next to wood gets one (as in 0.4.0's "twigs near
  branches"). That is what makes a bare crown dense, and what winter costs.
- The thinner twigs have no far end face.
- Last and first leaves that would hang next to a bare spot are not drawn.
- Birch sheds from the far edge inward like the other kinds (it went from
  the twigs outward since 0.2.0, also Claude's choice). With leaves that
  draw back to the branches, the other order ended as a ring of leaf
  blocks in the air around a bare middle.
- At "most leaves gone" only the main twig is drawn under the clump; the
  thinner twigs appear with the last leaves, when the clump has drawn back
  to a tuft and they could be seen.

### Limbs in layers: plan (2026-11-27, for 0.6.0)

**The owner's words (fixed).** On the 0.5.0 pictures: "the large branches
kind of spread in wierd directions. like more than one block up is not
natural, id rather you add more from the base." "like model it after real
trees, while maintaining the fulness of the leaves of what you had just
now". With two sketches: a trunk with level branches leaving it at many
heights on both sides ("Dont take the density to literally, but its to
show how you should layer the branches") and "a main branch with sub
branches branching of it, each with leaves in them". On Claude's sketch
of that reading: "Yes, build this but also make sure it looks good from a
3 perspective. Dont force every single space to be filled by leaves like
in sketch 2 but the tree should have volume still." "Like the volume in
you first build was good" (the crowns of 0.5.0, which are Dynamic Trees'
own). The small twigs inside leaf blocks stay as they are in 0.5.0.
Separately: leaves "should thin from the outer parts of the texture more"
(built, branch `thin-from-the-outside`).

**What that means in Dynamic Trees.** The tree's wood is Dynamic Trees'
own. Three kinds of trees exist: trees planted in new land (a stored
shape, never grows), trees grown from a seed (shaped step by step by the
species' growth logic kit), and trees that already stand (never change;
nothing to build).

**Parts, in order.**

1. `tools/limbs/` (Python, Fable): `jocode.py` reads and writes Dynamic
   Trees' tree codes and models thickness and leaves; `layers.py` makes
   trees with limbs in layers; `preview.py` draws them. Complicated: the
   shape is judged by eye and has to keep Dynamic Trees' rule that wood
   never touches other wood.
2. `dev/DevTreeShow` (Fable, small): plants listed codes in the real
   game and photographs them, so shapes are judged in the game, not in a
   drawing. **Stop here and show the owner one oak before going on.**
3. Stored shapes: `trees/dynamictrees/jo_codes/oak.txt` and `birch.txt`
   at the root of the jar, written by `layers.py write`, the same number
   of shapes per radius as Dynamic Trees has. Not complicated once 1 is
   right. To check: that Dynamic Leaves' tree pack wins over Dynamic Trees' own
   file, and that swamp oak, apple oak and tall birch (own code files in
   Dynamic Trees) are either left alone on purpose or get shapes too.
4. Growth for trees from seeds: a growth logic kit `dynamicleaves:layered`
   (off the trunk a branch never goes up or down, as Dynamic Trees' dark
   oak does it) named in replaced species files for oak and birch.
   Complicated enough for Fable: species files can only be replaced
   whole, and the kit changes every oak and birch that still grows.
5. A setting to turn the new shapes off (`layeredLimbs`), game tests,
   self test, measurements, README and changelog (Opus), then a separate
   Fable review of whatever Opus wrote.

**Tricky spots.**

- Wood that touches other wood grows together: limbs on heights right
  above each other must point different ways. `TreeBuilder.grow` refuses
  such blocks, so the generator cannot write a code the game rejects.
- Every branch end gets a 5x4x5 leaf cluster. Volume is steered by where
  ends are, not by adding leaves: compare leaf counts per radius with
  Dynamic Trees' own (oak 118, 180, 225, 300, 342, 376, 368 for radius 2
  to 8) and stay near them.
- Many limbs make a thick trunk foot (thickness is the square root of
  the summed squares of what hangs on a block).
- Dark oak's limbs are level already (no steps up off the trunk in its
  stored shapes): leave it.
- The self test and several game tests plant "the largest stored shape"
  of a species and compare counts: new code files change those numbers.

**To test.** Every written code decodes to the tree it was made from and
has no wood touching other wood (Python check when writing, game test
that plants every stored shape and finds it whole); no branch block off
the trunk has wood under it more than once per limb; leaf counts per
radius within a set share of Dynamic Trees' own; the growth kit never
picks up or down off the trunk (game test growing a tree with bone meal
pulses); the setting off gives Dynamic Trees' shapes back.

### Limbs in layers as built (2026-11-27, 0.6.0)

**The owner's words on the first pictures from the game (fixed).** "Id
say the branches going straight up should probably only be half or
2/3rds of a block it looks weird as it is now. Otherwise the tree loks
pretty great to me." "The medium one has a weird protruding branch. I
consider that a bug and it should be eliminated." "If that protruding
branch was one block shorter itd be fine, i think". Asked whether he
meant the upright small twigs or the one-block step of a limb: "I cannot
make out a difference between A and B". So both: nothing on the tree goes
straight up for more than about two thirds of a block.

**What was built, and where it differs from the plan.**

- **Shapes are data files of the mod's own, not a tree pack override**
  (plan part 3). `LayeredLimbs` reads
  `data/<any>/tree_shapes/<species namespace>/<species>.txt` in a reload
  listener that runs right after Dynamic Trees' own, copies all codes out
  of `JoCodeRegistry`, clears it and registers them again with the mod's
  shapes in the place of Dynamic Trees' for those species. That way the
  setting can give Dynamic Trees' shapes back while the game runs, no
  file of Dynamic Trees is replaced whole, and a data pack can add
  species. 20 shapes per radius 2 to 8 for oak, apple oak, swamp oak
  (oak's numbers), birch and tall birch.
- **The growth rule is set in code, not in replaced species files** (plan
  part 4): every species with a shape file gets `dynamicleaves:layered`
  (`Species.setGrowthLogicKit`), and gets its own back when the setting
  is turned off. The rule takes Dynamic Trees' weights and, off the
  trunk, sets up and down to zero unless wood is there already.
- **No step up at all.** The plan let a limb step up one block once.
  After the owner's words above the generator makes every limb level,
  `layers.py write` refuses a shape with wood on wood off the trunk, and
  the game test demands zero.
- **No limb sticks out from under the crown.** In the lower half of the
  crown a limb is at most one block longer than what covers it from
  above (the limb two heights up on its side, the limb four heights up
  less one, or two blocks for the side limbs of the turned layer right
  above). The upper half is the roof and narrows by itself. Layers with
  a limb on one side only are rarer than first built (oak 1 in 10), and
  the crown's outline starts at 0.7 of the reach at the lowest layer.
- **Ends without leaves are cut back in the tool.** Dynamic Trees lets a
  branch end rot when no leaf is next to it and smothers leaves deep in
  a crown; `jocode.leaves` models stamping, smothering and three rounds
  of aging, and the generator cuts bare ends until none is left.
- **Short upright mini branches** (`shape/MiniBranch`, `upright`): a
  mini branch that grows straight up or down has a main twig of 1 to 3
  pixels (2 to 4 from inside a thicker limb) instead of 9 to 12, with
  its thinner twigs spread over the limb's stub below the block's side.
  With the stub that is 8 to 10 pixels of upright wood. The chain link
  of the "more twigs" setting (`DEEP`) is unchanged. Known and accepted
  for now: a limb with a leaf spot above and one below shows the two
  upright mini branches in one line, about 1.2 blocks from end to end,
  because both stubs are Dynamic Trees' own.
- `dev/DevTreeShow` and `tools/limbs/layers.py show` as planned, with
  cameras that stand further back for taller trees and a close view.

**Claude's own decisions the owner may overrule.** Twigs that hang
straight down are short like those that stand up. Tall birch, apple oak
and swamp oak get shapes too. Dark oak is left alone. The cover rule
makes a long low limb shorter instead of adding a limb above it.

**Found on the way.**

- A game test's area (the `empty` template, 7 by 4 by 7) is walled in
  with barriers five blocks high, four blocks from the middle. A limb
  four blocks long below the wall's top ends inside the wall and Dynamic
  Trees leaves that piece out. This looked like rot for a while. The
  tests that compare a tree with its code plant it on a block of soil
  six blocks up.
- `/dt settree` gives every tree radius 8, turns the shape, and lets
  ends rot partly at random. To plant exactly as new land does:
  `species.getJoCode(code).setCareful(false).generate(new
  DynamicTreeGenerationContext(..., radius, true))`.
- A crown of limbs in layers is closed like a dome: no leaf block is
  open both to the sky and to the ground. The snow test makes such a
  spot (it takes out the leaves under an outer leaf block), under the
  open sky and in a batch of its own; the fruit tests only need air
  under a leaf.
- Vanilla applies reload listeners in the order they were added; adding
  at `EventPriority.LOWEST` puts Dynamic Leaves' after Dynamic Trees'.

**Tests.** Eight in `LayeredLimbsGameTests` (66 in all): shape files read
as written; the rule off the trunk; Dynamic Trees holds exactly the
mod's shapes and the species grow by the rule; 15 stored shapes each of
oak, birch and tall birch planted as new land stand as their code says,
with no wood on wood off the trunk, no limb sticking out, limbs on four
heights or more and a crown; a young oak grown with 200 pulses has level
limbs only; the setting off and on. `miniBranchBoxes` covers the upright
kind. The client self test passes on the new shapes (568 checks, 37
looks photographed, a whole year).

### 0.7.0: the name Dynamic Leaves, Dynamic Trees' branches by default (2026-11-27)

coolcritter22 looked at Dynamic Trees' own oaks bare (picture
`stage7/dynamic-trees-oaks-bare.png`) and liked them better than the
limbs in layers. He also chose to keep the mod as the link between
Dynamic Trees and Seasons, under the name Dynamic Leaves.

- `layeredLimbs` is `false` by default. Nothing else of 0.6.0 changed:
  the shapes, `tree/LayeredLimbs`, `tree/LayeredLogic` and
  `tools/limbs/` stay, so the setting still works. The short upright
  twigs (`shape/MiniBranch`) and the leaves that thin from the outside
  stay on.
- The tests of the layered shapes turn the shapes on themselves with
  `LayeredLimbs.use(true)` and put the setting's state back in a
  `finally`. Both run in one call, so no other test sees the change.
  `dynamicTreesHoldsTheLayeredShapes` also checks that by default the
  species keep Dynamic Trees' shapes and growth.
- Renamed: mod id `foliage` to `dynamicleaves` (resources, ids, settings
  files, command, test batches, Gradle properties such as
  `-Pdynamicleaves.selfTest`), package and main classes (`Foliage` to
  `DynamicLeaves`, `FoliageConfig` to `DynamicLeavesConfig` and so on).
  Words of the game that hold "foliage" (`foliageColorMultiplier`,
  `getAverageFoliageColor`) are the game's and stay.

## Tests

- **Game tests** (`./gradlew runGameTestServer`): the rules as pure checks,
  and in the world with real Dynamic Trees trees planted by command. Nothing
  may depend on the test's random position.
- **Self test** (`./gradlew runClient -Pdynamicleaves.selfTest=true` under xvfb,
  Sodium in `run/mods`): plants a fixed scene, photographs it from fixed
  views, counts faces per leaf block and checks pictures in numbers.
- **Measurements** (stage 4): server time per tick in a swap, light work,
  faces and memory per season, redraw at a clock step, against the same
  forest without the mod.
- **Review:** a separate helper pass against this plan and the design doc
  when stage 4 is done; one pass, findings fixed, tests run again.
