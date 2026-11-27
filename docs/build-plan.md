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
- The **real block** is a leaf (green months) or a placeholder (winter),
  swapped once per transition, at night, quietly, a chunk at a time, plus
  catch-up for chunks that were away.
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
| 3 | 0.3.0 | Real blocks: own leaf kind, placeholder, nightly swap, catch-up, Dynamic Trees season manager, note to Distant Horizons | two proofs first (below), game tests of every row of "Cases thought through", long run |
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

### Tricky spots, stage 3

- **Proof 1:** Dynamic Trees accepts the placeholder (second
  `LeavesProperties` of the same family, block built without
  `forceSolidOn`): twigs keep support through many tree updates, no leaves
  grow into the spot, hydration stays right, cutting a branch removes them.
  Fallback: one mixin for the rot rule.
- **Proof 2:** a chunk left unsaved between nights comes back identical
  after unload and reload (the swap is a pure function of the date).
- Quiet write only: `LevelChunk.setBlockState(pos, state, false)`,
  `ServerChunkCache.blockChanged(pos)`, `PathTypeCache.invalidate(pos)`.
  Never `Level.setBlock`, never neighbor or shape updates.
- After changes to a chunk that is not sent yet, call
  `ChunkMap.waitForLightBeforeSending`.
- Clients redo light on their render thread with no budget: feed changes
  slowly (blocks per tick cap), also after a time jump.
- `updateHydro` wipes extra properties unless
  `getLeavesBlockStateForPlacement` carries them; new leaves grown in the
  bare months must become placeholders.
- The leaf block's default state must be a full leaf (Distant Horizons falls
  back to it for unknown stored states).
- Two loot tables per leaf kind (`blocks/<leaf>`, `trees/leaves/<name>`).
- Snow lying on a leaf that becomes a placeholder must go with it.

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
