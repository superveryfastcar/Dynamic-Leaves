# Foliage

Leaves with depth for the trees of Dynamic Trees. A crown is no longer a pile of plain leaf cubes: it is built from leaf faces on its outside, leaf planes and round leaf cards inside, and small clumps of leaves at the tips of its branches. It also needs fewer faces than the plain cubes.

Foliage is a mod for **Minecraft 1.21.1 on NeoForge** (built against NeoForge 21.1.267). It is an addon for [Dynamic Trees](https://modrinth.com/mod/dynamictrees) 1.7.2 and needs the [Calendar](https://github.com/superveryfastcar/Calendar) and Seasons mods.

> **Version 0.1.0 is the first of four steps.** It brings the leaf shapes. Leaves that fall in autumn, bare branches in winter and buds in spring come in the next steps (see [Roadmap](#roadmap)). Until then autumn, winter and spring look as Seasons draws them today, on plain cubes.

- [What it does](#what-it-does): for players and pack makers
- [Settings](#settings)
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

Where leaves cannot be see-through (the game's "fast" graphics, or Sodium's fast leaves), Foliage draws only the outside of the crowns: about 2 faces per leaf block instead of 6.

Nothing in the world changes: this version only changes how leaves are drawn. A server does nothing for it, and worlds can be opened without the mod again at any time.

---

## Settings

All settings are in `config/foliage-client.toml`, and in the game under Mods, Foliage, Config. They are each player's own and take effect as soon as they are changed.

| Setting | Default | What it does |
| --- | --- | --- |
| `shape` | `MIXED` | The shape of the crowns: `PLAIN`, `SHELL`, `BUSHY`, `TUFTS` or `MIXED`. |
| `fastLeaves` | `false` | The fewest faces, whatever the shape: only the outside of the crown and one leaf plane inside each block. For slow computers. |
| `maxFaces` | `6` | The most faces a single leaf block may be drawn from, 6 to 12. With 6 no leaf block costs more than in the unmodded game. Higher values give richer tips: a second leaf card, and clumps that sit on a twig instead of right on their branch. |
| `tipSides` | `3` | A leaf block with at least this many of its six sides open counts as a tip of the crown. |
| `clumpSides` | `4` | A leaf block with at least this many open sides is an outermost tip and becomes a clump (tufts, mixed). |
| `cardWidth`, `cardHeight` | `30`, `26` | The size of a leaf card in pixels (a block is 16 wide). |
| `tuftSize`, `tipSize` | `12`, `8` | Tufts: the size of a clump at a tip, and at an outermost tip. |
| `clumpSize` | `10` | Mixed: the size of a clump. |

---

## Other mods and resource packs

- **Sodium**: works, and is what Foliage is tested with. Hidden faces are left out by Foliage itself, so nothing depends on how a renderer hides faces.
- **Seasons**: gives the leaf colors. In this version, while Seasons shows leaves thinning out (late autumn to early spring), Foliage steps aside and leaves are drawn as plain cubes with Seasons' thinned pictures.
- **Resource packs**: the leaf cards are made from whatever leaf textures are loaded, so a pack that changes the leaves changes the cards with them. Animated or non-square leaf textures get no cards (the other pieces still work).
- **Leaves from other mods** (addons of Dynamic Trees): get outer faces, planes and clumps by themselves if they are drawn as a plain cube with one texture. For leaf cards and matching twigs, list the texture in a file `assets/minecraft/atlases/blocks.json` of a resource pack or mod:

  ```json
  {"sources": [{"type": "foliage:leaf_parts", "leaves": [
      {"texture": "somemod:block/maple_leaves", "twigs": "foliage:block/twigs_brown"}
  ]}]}
  ```

  `twigs` names a set of twig pictures (`foliage:block/twigs_brown`, `twigs_birch` or `twigs_dark`, or your own: pictures named `<name>_0`, `<name>_1` and so on in the block textures).
- **Trees that are not Dynamic Trees** (the game's own leaf blocks, placed by hand or by other mods) are not touched.

---

## Roadmap

| Version | What |
| --- | --- |
| 0.1.0 | Leaf shapes (this version). |
| 0.2.0 | The leaf calendar as a look: leaves thin out and fall over the last days of autumn, bare twigs in winter, buds and young leaves over the first days of spring, in the new shapes. |
| 0.3.0 | Real bare branches: leaf blocks of bare trees are swapped for an empty placeholder, so rain, snow and light pass through winter crowns. |
| 0.4.0 | Falling leaves, snow on crowns, fruit, measurements. |

The whole design is in `docs/build-plan.md` and the design document it links to.

---

## For developers of Foliage

### How it works

Everything in this version happens in the game client while it builds the picture of a chunk.

- `shape` package: plain arithmetic, no drawing. `Surround` reads the six neighbors of a leaf block into one number (open sides, where the wood is, where the heart of the crown is, whether snow lies on top). `ShapeRules` turns that and the settings into a `Recipe`, one number that says which pieces the block gets. `CardPicture` cuts the picture of a leaf card.
- `client.model` package: `LeafModel` wraps the leaf block's own model. The game asks it once per block for the recipe (`getModelData`) and then for the faces (`getQuads`). The faces of a recipe are built once (`QuadMaker`) and shared by every block with that recipe. `LeafModels` holds the settings and puts the wrappers in place when the game has loaded its models.
- `client.sprite` package: `LeafPartsSource`, an atlas source that makes the card pictures from the loaded leaf textures.
- `util` package: `PosHash`, fixed random numbers per position.

Things that are easy to get wrong:

- The model is asked on several chunk-building threads at once. Settings are one unchanging object that is swapped as a whole; built faces are kept in a table that is read without a lock (`RecipeTable`).
- Faces toward covered sides are simply not in the recipe. The model answers the game's question for each of the six sides with nothing and gives everything in the answer for "no side". This works the same in the game's own chunk builder and in Sodium.
- A card never stands at 45 degrees, where the cards of diagonal neighbors would lie in one plane and flicker.
- A clump sits against the side its block hangs on to (wood first, then the heart of the crown). A leaf block with snow on top is never made a clump.

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

runs the automatic tests (the `gametest` package) on a server without a screen: the rules for every possible set of open sides, and real trees of every kind planted by command, with the faces per leaf block held against limits.

```
./gradlew runClient -Pfoliage.selfTest=true
```

runs the self test (the `dev` package) in a real game client. It plants a fixed scene, shows it in every shape, checks what the chunk builder was handed and saves pictures to `run/screenshots/`. Put Sodium into `run/mods/` to test with Sodium. On a machine without a screen:

```
LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a -s "-screen 0 1600x900x24" ./gradlew runClient -Pfoliage.selfTest=true
```

The result is the line with `[Foliage self test] RESULT:` in the log.

### Versions

`0.x.0` are the steps of the roadmap. The version is set in `gradle.properties` (`mod_version`), and every change is noted in `CHANGELOG.md`.

### Licence

MIT, see `LICENSE`. By coolcritter22.
