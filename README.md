# Foliage

Leaves with depth for the trees of Dynamic Trees, and a year for them to live through. A crown is no longer a pile of plain leaf cubes: it is built from leaf faces on its outside, leaf planes and round leaf cards inside, and small clumps of leaves at the tips of its branches. In the last days of autumn the leaves thin out and fall, tree by tree and from the edge of each crown inward. In winter the trees stand bare, with twigs on their branches, and bare means bare: light, rain, snow and players pass through the crown. In the first days of spring the twigs bud, and small light leaves grow back from the twigs outward. All of it needs fewer faces than the plain cubes.

Foliage is a mod for **Minecraft 1.21.1 on NeoForge** (built against NeoForge 21.1.267). It is an addon for [Dynamic Trees](https://modrinth.com/mod/dynamictrees) 1.7.2 and needs the [Calendar](https://github.com/superveryfastcar/Calendar) and Seasons mods.

> **Version 0.3.0 is the third of four steps.** It makes bare trees really bare: while a leaf spot has no leaves, its leaf block is swapped for an empty stand-in that lets light, weather and players through. Falling leaves and snow on crowns come with the next step (see [Roadmap](#roadmap)).
>
> **From this version on, every player needs Foliage to join a server that has it**, because the stand-ins are blocks of the mod. Read [Taking the mod out of a world](#taking-the-mod-out-of-a-world) before you remove it.

- [What it does](#what-it-does): for players and pack makers
- [Settings](#settings): each player's own, and the rules of the world
- [Other mods and resource packs](#other-mods-and-resource-packs)
- [For developers of Foliage](#for-developers-of-foliage): how it works, building, testing

---

## What it does

### Leaf shapes

Every leaf block of a Dynamic Trees tree is drawn from a few pieces, chosen by where it sits in the crown:

| Piece | What it is |
| --- | --- |
| Outer faces | The sides of the block, only where a side is open to the air. |
| Inner planes | Flat leaf pictures through the middle of the block. They give depth behind the holes of the outer faces. |
| Cards | Round patches of leaves that stand at a slant and reach out past the block. They break up the square outline of a crown. |
| Clumps | A smaller cube of leaves that sits on its branch, at the tips of a crown. |
| Twigs | A bare twig that carries a clump: it grows from the branch, and the clump sits at its far end (only with a raised face limit, see Settings). |

A leaf block that is buried inside the crown is not drawn at all, and no face is drawn between two leaf blocks.

There are five shapes to choose from:

| Shape | Look |
| --- | --- |
| Plain | The unmodded look: one plain cube per leaf block. |
| Shell | Only the outside of the crown, with leaf planes inside the blocks for depth. The calmest shape. |
| Bushy | As shell, with round leaf cards that reach out of the blocks. Softer, rounder crowns. |
| Tufts | Leaf blocks at the edge of the crown shrink to small clumps on their branches. Airy crowns with the limbs showing through. |
| Mixed (the default) | Bushy crowns, with small clumps at the very tips and on thin side branches. |

No two neighboring leaf blocks look alike: each position turns and shifts its pieces its own way, and always the same way, so a tree does not change when the chunk is drawn again.

All kinds of leaves that Dynamic Trees itself adds get the shapes: oak, birch, spruce, jungle, acacia, dark oak, mangrove, cherry, azalea and flowering azalea, with their undergrowth.

### The leaf calendar

Oak, birch and dark oak lose their leaves at the end of autumn and grow them back at the start of spring. The days are those of the Calendar mod, and the leaves follow them wherever Seasons says there is a winter (not in jungles, deserts or other places without one). Spruce, jungle, acacia, mangrove, cherry and azalea stay green.

**Autumn.** The leaves fall during the last 7 days of autumn. Not all trees go at once:

- Kinds start on different days: birch first, oak a day later, dark oak a day after oak.
- Trees of one kind start up to a day apart, depending on where they stand. Trees close to each other go together, so a forest thins in soft patches and not tree by tree at random.
- One tree takes about 4 days. Its crown thins from the far edge inward (birch: from the twigs outward), one leaf block after another: about a third of the leaves gone, then two thirds, then a few last leaves, then none.
- On the last day of autumn every tree that sheds is bare.

**Winter.** A bare crown is drawn as twigs on the branches: every leaf spot next to wood carries a bare twig, the rest is air. That is 0.7 to 0.9 faces per leaf block instead of 6.

**Spring.** The leaves come back during the first 7 days of spring, in the same order of kinds and patches. A tree first shows buds on its twigs for a day. Then small, light leaves come out at the twigs and spread outward: first a few, then half-grown ones, then the full crown. On day 7 every tree is in full leaf.

The leaves do not change all the time but 4 times a day, and only the part of the landscape whose look really changed is drawn again, spread over some seconds.

With the plain shape the year is shown too: the cubes stay cubes, and only their pictures thin out.

### Real bare branches

Dynamic Trees keeps a tree alive through its leaf blocks: a twig only holds while leaf blocks surround its tip. So the leaf blocks of a bare tree cannot simply be removed, or the twigs would rot and the tree would grow back in another shape. Instead, a leaf spot that has lost its leaves holds a **bare block** ("Bare Twigs") until its leaves return. For the tree it is a leaf like any other. For everything else it is next to nothing:

- **You walk, shoot and look through it.** It has no collision and no outline: the crosshair goes through it to whatever is behind, and a block can be placed where it is. Animals and monsters take it for open air.
- **Light passes through it.** The ground under a bare tree is as bright as open land, and the last leaves inside a crown that is going bare are lit by the sky instead of standing in the dark.
- **Rain and snow fall through it.** Snow that lay on a leaf block goes with its leaves, and snow settles on the ground under bare trees.
- **It drops nothing.** A bare tree that is cut down gives its wood as always, and no leaves, seeds or sticks from its crown.
- **It burns like wood, not like leaves.** Fire eats its way into a bare crown as slowly as it eats logs, and does not run along the outside of it as it does along leaves. (It has to be able to burn at all: fire only starts in open air, and a bare block is not air, so a crown of bare blocks that could not burn would keep fire away from its own twigs.) Both numbers are settings.
- **Water stays where it is.** Water does not flow into a bare block, as it does not flow into leaves. A stream that a crown holds back in summer does not break through in winter.
- **The tree stays as it is.** Its twigs hold, a tree that grows in winter grows bare, and in spring every spot gets its leaf block back with nothing lost.

A spot holds its bare block from the stage "a few last leaves" in autumn until "first leaves" in spring, so the last and the first leaves of a crown already hang in open air. How a tree is drawn never depends on which of the two blocks is there: both are drawn by the same rule from the date.

The server swaps the blocks a little at a time, the trees nearest to the players first (see the settings under [`bareBranches`](#the-rules-of-the-world-configfoliage-commontoml)). When a whole forest goes bare at once, for example when you come back in winter to land you last saw in summer, the trees around you are done within seconds and the ones far away some time later. Until a tree's turn comes it already looks bare, but still gives shade and can be stood on.

Every plain kind of Dynamic Trees leaves has a bare block, also those of addons. Palm fronds and the wart blocks of the Nether trees have none; if they are listed to shed, they change their look only.

### Fewer faces than plain cubes

The unmodded game draws 6 faces for every leaf block, also the ones buried in the crown. Foliage never draws more than 6 for any leaf block (with the default settings), and for most far fewer. Measured on full-grown trees of Dynamic Trees, standing alone, in faces per leaf block:

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

Trees with thin crowns (spruce, acacia) save least, because nearly all their leaf blocks are at the edge. In a forest the crowns touch and the numbers are lower still.

Through the year, with the default settings (mixed shape, at most 6 faces per leaf block):

| Tree | Summer | Winter | Costliest day of the year |
| --- | --- | --- | --- |
| Oak | 4.2 | 0.9 | 5.1 |
| Birch | 3.9 | 0.8 | 5.1 |
| Dark oak | 4.0 | 0.7 | 5.3 |

The costliest days are the middle of the week of new leaves in spring: young leaves are small clumps, and a small clump shows more of its sides than a full leaf block that is covered by its neighbors. It is still less than the 6 of the plain cubes.

Where leaves cannot be see-through (the game's "fast" graphics, or Sodium's fast leaves), Foliage draws only the outside of the crowns: about 2 faces per leaf block instead of 6. The last leaves of autumn and the first of spring are not drawn there (a few leaves cannot be shown without see-through pictures).

Nothing in the world changes: this version only changes how leaves are drawn. Worlds can be opened without the mod again at any time.

---

## Settings

There are two files. Both can also be changed in the game under Mods, Foliage, Config, and both take effect as soon as they are changed.

### The look: `config/foliage-client.toml`

Each player's own.

| Setting | Default | What it does |
| --- | --- | --- |
| `shape` | `MIXED` | The shape of the crowns: `PLAIN`, `SHELL`, `BUSHY`, `TUFTS` or `MIXED`. |
| `fastLeaves` | `false` | The fewest faces, whatever the shape: only the outside of the crown and one leaf plane inside each block. For slow computers. |
| `maxFaces` | `6` | The most faces a single leaf block may be drawn from, 6 to 12. With 6 no leaf block costs more than in the unmodded game. Higher values give richer tips: a second leaf card, and clumps that sit on a twig instead of right on their branch. |
| `winterTwigs` | `NEAR` | Which leaf spots of a bare tree carry a twig: `NONE` (bare branches only), `NEAR` (the spots next to wood) or `MORE` (also spots deep in the crown, see `twigDepth`). |
| `tipSides` | `3` | A leaf block with at least this many of its six sides open counts as a tip of the crown. |
| `clumpSides` | `4` | A leaf block with at least this many open sides is an outermost tip and becomes a clump (tufts, mixed). |
| `cardWidth`, `cardHeight` | `30`, `26` | The size of a leaf card in pixels (a block is 16 wide). |
| `tuftSize`, `tipSize` | `12`, `8` | Tufts: the size of a clump at a tip, and at an outermost tip. |
| `clumpSize` | `10` | Mixed: the size of a clump. |
| `twigDepth` | `3` | With `winterTwigs = MORE`: leaf spots at least this deep in the crown also carry a twig (1 is the far edge, most trees go up to 4). |
| `firstSize` | `8` | The size of the clump of first leaves in spring. |
| `halfSize` | `12` | The size of the clump of half-grown leaves. |

### The rules of the world: `config/foliage-common.toml`

The file has two sections.

**`leafCalendar`**: when the leaves fall and come back, and for which trees. On a server these are the server's: it sends them to every player who joins, and again when the file changes. Days are days of the Calendar mod, and parts of a day are allowed.

| Setting | Default | What it does |
| --- | --- | --- |
| `enabled` | `true` | `false`: trees keep their leaves all year. |
| `fallDays` | `7` | The leaves fall during this many days at the end of autumn. On the last of them every tree that sheds is bare. |
| `returnDays` | `7` | The leaves come back during this many days at the start of spring. On the last of them every tree is in full leaf. |
| `treeDays` | `4` | How many of those days one tree takes. Lower than the two above, so that not all trees change at once. A tree that starts late takes fewer days, so that it is done in time. |
| `budDays` | `1` | In spring a tree first shows buds only. This many of its days go to the buds. |
| `shiftDays` | `1` | Trees of one kind start up to this many days apart, depending on where they stand. |
| `shiftPatch` | `16` | How many blocks across the patches are within which trees start close together. |
| `stepsPerDay` | `4` | How many times a day the leaves move on. More steps are smoother, but the landscape is drawn again more often while leaves fall or grow. |
| `spotShare` | `0.4` | The share of a tree's days that one leaf block takes for its own change. 1: all leaf blocks of a tree change together. |
| `depthWeight` | `0.6` | How strictly the leaf blocks of a tree go in order of their depth in the crown. 1: strictly, 0: in no order. |
| `deepest` | `4` | Dynamic Trees numbers leaf blocks by how well they are supplied: 1 at the far edge, higher toward the twig. This number or more counts as right at the twig. |
| `kinds` | birch, oak, dark oak | The kinds of leaves that fall, one line each: the leaf block's id, the day its kind starts on (0 for the first kinds), and the order (`outside_in`: the edge of the crown goes first, or `inside_out`). All other leaves stay green. |

The default list of kinds:

```toml
kinds = ["dynamictrees:birch_leaves, 0, inside_out", "dynamictrees:oak_leaves, 1, outside_in", "dynamictrees:dark_oak_leaves, 2, outside_in"]
```

If the fall or the return is set longer than autumn or spring are in the Calendar mod's settings, it is cut to the season's length.

**`bareBranches`**: how the server swaps leaf blocks for bare blocks. Only the server reads these.

| Setting | Default | What it does |
| --- | --- | --- |
| `bareBlocks` | `true` | `false`: leaf blocks are never swapped. Bare trees are then only drawn bare: they still give shade, hold snow and stop arrows. Bare blocks already in the world are turned back into leaf blocks as players come near. |
| `chunksPerTick` | `64` | The most chunks the server looks at in one tick. Most are done at a glance: a chunk without leaves, or one where nothing can be wrong at this time of year, costs next to nothing. |
| `changesPerTick` | `300` | The most blocks the server swaps in one tick. Every swap is sent to the players in view, whose games then work out the light anew. Lower this if players stutter when leaves fall; raise it if bare forests take too long to let the light through. |
| `millisPerTick` | `1.0` | The most time the server spends on this in one tick, in thousandths of a second. A tick lasts 50. |
| `twigsBurn` | `5` | How easily fire eats a bare block, 0 to 300. A log has 5, leaves have 30 to 60. With 0 bare blocks never burn, and fire cannot reach the twigs and branches inside a bare crown. |
| `twigsSpreadFire` | `0` | How easily fire starts in the open air next to a bare block, 0 to 300. A log has 5, leaves have 30 to 60. With 0 fire does not jump along the outside of a bare crown, though it can still eat its way in. |

### Taking the mod out of a world

Bare blocks are blocks of Foliage. If the mod is removed while a world holds them, the game turns them into air, and Dynamic Trees then lets the twigs of those trees rot and grow anew. To avoid that:

1. Set `bareBlocks = false` (or wait for summer, when no tree holds a bare block).
2. Visit your land. The server gives the leaf blocks back in every chunk that comes into view of a player, within a few seconds.
3. Then remove the mod.

Chunks that nobody visits in between keep their bare blocks. A command that restores a whole world at once is planned for 0.4.0.

---

## Other mods and resource packs

- **Sodium**: works, and is what Foliage is tested with. Hidden faces are left out by Foliage itself, so nothing depends on how a renderer hides faces.
- **Seasons** (0.4.0 or newer): gives the leaf colors, and says where there is a winter. Seasons' own look of thinning trees is switched off for the leaves Foliage draws, so the two do not thin the same tree twice. The game's own leaf blocks (not Dynamic Trees) are still Seasons' to draw.
- **Calendar**: gives the days. Without a calendar for a world (before its date has arrived from the server) every tree is drawn in full leaf.
- **On a server**: the server and every player need Foliage. The server holds the rules of the leaf calendar, sends them to the players and swaps the blocks of bare trees. A player with Foliage can still join a server without it; the trees then go through the year as a look only, by the rules of the player's own file.
- **Snow mods and falling snow**: snow cannot lie on a bare crown, because nothing rests on a bare block. It falls through to the ground. Snow on the branches of bare trees is planned for 0.4.0.
- **Distant Horizons**: not tested with bare blocks yet. That is part of 0.4.0.
- **Resource packs**: the leaf cards and the pictures of thinned, last, young and half-grown leaves are made from whatever leaf textures are loaded, so a pack that changes the leaves changes them all. Animated or non-square leaf textures get none of them: such trees still go through the year (fewer and smaller pieces, then twigs), but with their one full picture.
- **Fast graphics** (leaves that are not see-through): crowns are drawn from their outside only. Thinned leaves are shown as full ones, and the last leaves of autumn and the first of spring are not drawn.
- **Leaves from other mods** (addons of Dynamic Trees): get outer faces, planes and clumps by themselves if they are drawn as a plain cube with one texture. For leaf cards and matching twigs, list the texture in a file `assets/minecraft/atlases/blocks.json` of a resource pack or mod:

  ```json
  {"sources": [{"type": "foliage:leaf_parts", "leaves": [
      {"texture": "somemod:block/maple_leaves", "twigs": "foliage:block/twigs_brown"}
  ]}]}
  ```

  `twigs` names a set of twig pictures (`foliage:block/twigs_brown`, `twigs_birch` or `twigs_dark`, or your own: pictures named `<name>_0`, `<name>_1` and so on in the block textures, and `<name>_buds_0` and so on for the same twigs with buds).

  To let such leaves fall in autumn as well, add their leaf block to `kinds` in `config/foliage-common.toml`.
- **The pictures of the year** are made by a few numbers that a resource pack can change in the same file, with a field `pictures` next to `leaves`:

  ```json
  {"sources": [{"type": "foliage:leaf_parts", "leaves": [],
      "pictures": {"thin": 0.33, "thinner": 0.67, "last": 0.85,
                   "young_gone": 0.5, "young_light": 1.15, "half_gone": 0.15, "half_light": 1.07}}]}
  ```

  `thin`, `thinner` and `last` are the share of a texture's leaves that are gone in the three pictures of autumn. `young_gone` and `half_gone` are the share not out yet in the two pictures of spring, and `young_light` and `half_light` say how much lighter those leaves are (1.15 is 15 percent lighter). The values shown are the defaults.
- **Trees that are not Dynamic Trees** (the game's own leaf blocks, placed by hand or by other mods) are not touched.

---

## Roadmap

| Version | What |
| --- | --- |
| 0.1.0 | Leaf shapes. |
| 0.2.0 | The leaf calendar as a look: leaves thin out and fall over the last days of autumn, bare twigs in winter, buds and young leaves over the first days of spring, in the new shapes. |
| 0.3.0 | Real bare branches (this version): leaf blocks of bare trees are swapped for an empty stand-in, so rain, snow, light and players pass through winter crowns. |
| 0.4.0 | Falling leaves, snow on the branches of bare trees, trees that rest in winter (no growth unless a setting allows it) and bear fruit in its season, a command to restore a whole world, measurements in large forests and with Distant Horizons. |

The whole design is in `docs/build-plan.md` and the design document it links to.

---

## For developers of Foliage

### How it works

The look happens in the game client while it builds the picture of a chunk. The server holds the rules of the leaf calendar, sends them, and swaps leaf blocks for bare blocks and back.

- `season` package: the leaf calendar, plain whole-number arithmetic, the same on a server and a client. `LeafClock` is one moment of the year: it answers for a leaf block at a position which `LeafStage` it is at (full, thin, thinner, last leaves, bare, buds, first leaves, half grown). `SeasonSettings` are the rules, `LeafCalendar` holds the ones in use and makes the clock from the Calendar mod's date.
- `shape` package: plain arithmetic, no drawing. `Surround` reads the six neighbors of a leaf block into one number (open sides, where the wood is, where the heart of the crown is, whether snow lies on top). `ShapeRules` turns that, the settings and the stage into a `Recipe`, one number that says which pieces the block gets. `LeafLook` joins the clock, the question "is there a winter here" and the shape rules for one chunk-building thread. `CardPicture` cuts the picture of a leaf card, `LeafPictures` works out the pictures of thinned and young leaves.
- `client.model` package: `LeafModel` wraps the leaf block's own model. The game asks it once per block for the recipe (`getModelData`) and then for the faces (`getQuads`). The faces of a recipe are built once (`QuadMaker`) and shared by every block with that recipe. `LeafModels` holds the settings and puts the wrappers in place when the game has loaded its models.
- `client.sprite` package: `LeafPartsSource`, an atlas source that makes the card pictures and the pictures of the year from the loaded leaf textures.
- `client.ClientLeafClock`: looks at the calendar once a tick, swaps the clock when a step has passed and asks Seasons for a redraw when the look of any tree changed.
- `block` package: the bare blocks. `BareLeavesBlock` is a leaf block of Dynamic Trees with everything a player could notice taken away. `BareLeavesProperties` is its "leaf kind" for Dynamic Trees and passes every question (tree family, how water spreads, how well it burns) on to the kind of the leaf it stands in for. `Twins` makes one bare block for every plain leaf kind when the game registers blocks, pairs them up and tells every tree species that the bare blocks are leaves of its own.
- `world` package: `SpotRule` says which of its two blocks a leaf spot holds at a moment of the leaf calendar. `LeafWalker` (one per world, on the server) looks at the chunks in view of the players at every step of the calendar and swaps what is wrong, within the limits of `WalkSettings`.
- `client.BareBlockStates`: a small resource pack made in memory that gives every bare block a block state file, so that the game does not report missing models. The models themselves are put in place by `LeafModels`: a bare block is drawn by the model of its leaf.
- `network` package: the rules of the world, sent to players when they join and when the file changes.
- `util` package: `PosHash`, fixed random numbers per position.

Things that are easy to get wrong:

- The model is asked on several chunk-building threads at once. Settings and the clock are unchanging objects that are swapped as a whole; built faces are kept in a table that is read without a lock (`RecipeTable`); each thread has its own `LeafLook`.
- Faces toward covered sides are simply not in the recipe. The model answers the game's question for each of the six sides with nothing and gives everything in the answer for "no side". This works the same in the game's own chunk builder and in Sodium.
- Whether a side is covered depends on the neighbor's stage too: a leaf block only covers its neighbors while it is in full leaf or in its first thinning. So the model works out the stage of each leaf neighbor, with the same rule.
- The stage is never stored. It is worked out from the date, the kind of leaves and the position each time a chunk is drawn, so a chunk drawn today and its neighbor drawn tomorrow can differ by one step until the redraw reaches both. The redraw after a step is Seasons' (spread over some seconds).
- A card never stands at 45 degrees, where the cards of diagonal neighbors would lie in one plane and flicker.
- A clump sits against the side its block hangs on to (wood first, then the heart of the crown). A leaf block with snow on top is never made a clump.
- A leaf block whose six neighbors are all leaves or solid blocks is "inside the crown": while leaves fall or grow it is drawn with one or two planes only. These blocks are buried in summer, and they would be the costliest part of autumn and spring otherwise.
- Callers that ask the model without its data (items, falling blocks, Dynamic Trees' falling tree) get the full leaf cube, whatever the season; for a bare block they get nothing, so a bare tree falls bare.
- The bare blocks are registered at the lowest priority. The game calls every mod once per priority, Dynamic Trees reads its tree packs at normal priority and registers its leaf blocks at the lowest, so only then do its blocks exist. The names of the bare blocks (`foliage:bare_<leaf block>`, with the mod id in front for other mods' leaves) are stored in worlds and must never change.
- A swap is written into the chunk quietly: the players are told and the light is worked out, but no neighbor is updated, because for the tree nothing has changed. A normal block change would wake every leaf block around it. Only what rested on a leaf that went bare is looked after (snow on top, a vine on its side).
- The walker only touches chunks whose changes the server passes on to players (ticket level 32 or lower, which covers everything in view). A change to any other chunk would never reach a player's game that holds an old copy of it.
- Dynamic Trees puts leaf blocks down itself: when a twig grows, when a sapling becomes a tree, when the supply of a leaf changes. Where that happens through a bare block, the block is asked which state to place and answers by the calendar. Where it happens through the species' own leaf block, a bare block next to the new leaf notices it and the walker puts the spot right in the next tick. Whatever is missed is found at the next step of the calendar.
- The game works out which block a spot holds from the date alone, never from what was there before, so nothing has to be stored and a world can be opened at any date.

### Building

```
./gradlew build
```

The jar is written to `build/libs/`. The build needs Java 21 and nothing else: Dynamic Trees, Calendar and Seasons are in `libs/`.

**Updating the libraries:** put the new jar into `libs/` under the name `<mod>-<version>.jar` and change the version in `gradle.properties`.

The twig pictures are drawn by `tools/make_twigs.py` (Python 3 with Pillow). They are part of the repository; the script is only needed to change them.

### Testing

```
./gradlew runGameTestServer
```

runs the automatic tests (the `gametest` package) on a server without a screen: the rules for every possible set of open sides, the leaf calendar step by step, real trees of every kind planted by command and taken through a whole year, with the faces per leaf block held against limits, and the bare blocks: a tree goes bare and back with nothing lost, grows in winter, is cut down bare, the light under it, places without a winter, and the limits per tick.

```
./gradlew runClient -Pfoliage.selfTest=true
```

runs the self test (the `dev` package) in a real game client. It plants a fixed scene, shows it in every shape and then day by day through autumn, winter and spring, checks what the chunk builder was handed, that the player's game holds the same blocks as the server and how bright it is under the crowns, and saves pictures to `run/screenshots/`. Put Sodium into `run/mods/` to test with Sodium. On a machine without a screen:

```
LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a -s "-screen 0 1600x900x24" ./gradlew runClient -Pfoliage.selfTest=true
```

The result is the line with `[Foliage self test] RESULT:` in the log.

### Versions

`0.x.0` are the steps of the roadmap. The version is set in `gradle.properties` (`mod_version`), and every change is noted in `CHANGELOG.md`.

### Licence

MIT, see `LICENSE`. By coolcritter22.
