# Changelog

All notable changes to Foliage, newest first. The version numbers are
explained in the README under "Versions".

## 0.7.0

The mod is now called Dynamic Leaves: the link between Dynamic Trees and
Seasons, for what the seasons do to the leaves of the trees.

- **New name.** Mod id `dynamicleaves` (was `foliage`), package
  `io.github.superveryfastcar.dynamicleaves`, settings files
  `config/dynamicleaves-client.toml` and `config/dynamicleaves-common.toml`,
  command `/dynamicleaves`. A world that had Foliage loses its bare blocks
  and its settings: run `/foliage restore` with 0.6.0 before you update.
- **Dynamic Trees' own branches again.** The limbs in layers of 0.6.0 are
  off by default (`layeredLimbs = false`); the setting turns them on. The
  short upright twigs of bare trees and the leaves that thin from the
  outside stay.

## 0.6.0

Trees shaped after real trees, after the pictures of 0.5.0.

- **Limbs in layers.** Oak, apple oak, swamp oak, birch and tall birch have
  a straight trunk and level limbs that leave it layer above layer, from
  low on the trunk to just under the top, each with side limbs at right
  angles. Off the trunk no wood goes up, and no limb sticks out from under
  the crown. The crowns have about as many leaf blocks as before. Trees
  that come with new land take one of 140 stored shapes per kind; trees
  that grow from a sapling grow by the new rule `foliage:layered`. Trees
  that already stand keep their wood. New setting `layeredLimbs` (section
  `trees` of `config/foliage-common.toml`, default on).
- **Shapes are data.** A data pack can replace the shapes or give other
  trees limbs in layers: `tree_shapes/<species namespace>/<species>.txt`,
  in the format of Dynamic Trees' own shape files. A tree pack can name
  the growth rule in a species file.
- **Leaves thin from the outside.** The leaves of a leaf picture that lie
  outside its middle go first and evenly, and the middle stays dense to
  the end. The round leaf cards thin from their own rim in the same way,
  and the small clumps, tufts and buds show the middle of the picture.
- **Short upright twigs.** A small branch of a bare tree that stands
  straight up or hangs straight down is now the short kind: with the
  limb's own stub it is 8 to 10 pixels of upright wood instead of 15 to
  19. Level ones are as they were.
- For developers: `tools/limbs/layers.py` writes the shapes, and
  `./gradlew runClient -Pfoliage.show=<file>` photographs trees in the
  game. 66 game tests.

## 0.5.0

A better bare tree and a better fall, after the first pictures of 0.4.0.

- **Small branches instead of flat twigs.** A leaf spot of a bare tree that
  touches wood now holds a small branch in the bark of its own tree, built
  the way Dynamic Trees builds its branches, only thinner: a main twig two
  pixels thick that grows out of the wood into the leaf spot, and up to
  three twigs one pixel thick that leave it in different directions.
  Sixteen different ones, so neighbors do not look alike. The 24 flat twig
  pictures and their script are gone; the bark is taken from the tree's
  branch, whatever resource pack it comes from. New setting `twigForks`
  (0 to 3). `winterTwigs = MORE` gives leaf spots deeper in the crown a
  thinner small branch that grows out of the spot they hang on to.
- **Leaf blocks get smaller as their leaves fall.** A leaf block first
  thins, then draws back toward its branch as a smaller clump, then is a
  small tuft of last leaves on the end of a twig, then is gone. The flat
  planes of a few specks are gone, and so is the jump from specks to twigs.
  The second leaf picture of autumn is fuller (half of the leaves gone
  instead of two thirds). Last leaves hang on twigs or against a neighbor
  that is still in leaf, never in the air next to a bare spot. Spring runs
  the same way backward: a bud on the end of every main twig, a tuft of
  first leaves there, a half-grown clump, the full crown. New settings
  `fallSize`, `lastSize`, `budSize`.
- **Birch sheds from the far edge inward now, like oak and dark oak.** It
  used to go from the twigs outward, which left a ring of leaf blocks in
  the air around a bare middle. The order is still a setting per kind
  (`kinds` in `config/foliage-common.toml`). A settings file written by an
  earlier version keeps its birch line: change `inside_out` to
  `outside_in` there, or delete the file.
- **Leaves let go when a leaf block changes in view.** A leaf block that
  loses leaves while the player looks its way drops a few at that moment
  (`burst` in the section `fallingLeaves`, default 3, 0 for none), under
  the same cap as the other leaves in the air. Blocks behind the player
  are skipped, and the look around is spread over ticks.
- **What it costs.** A bare crown used to be nearly free (0.7 to 0.9 faces
  per leaf block). Small branches are real boxes: winter now costs 3.0
  (dark oak) to 3.9 (oak) faces per leaf block, half to two thirds of the
  6 of the unmodded game. The costliest day of the year is 5.2 to 5.8, so
  every tree stays under the 6 of the plain cubes on every day.
  `twigForks` and `winterTwigs` bring winter down for those who need it
  (`winterTwigs = NONE`: next to nothing); `winterTwigs = MORE` goes over
  6 on some trees. The numbers are in `docs/measurements.md`.
- The game's settings screen has names for the settings of the falling
  leaves, which were missing in 0.4.0.
- Resource packs and addons that listed `twigs` for their leaves in
  `assets/minecraft/atlases/blocks.json` need not change anything: the
  field is ignored.

## 0.4.0

The rest of the year: the air, the tree itself, and the way out.

- Single leaves drift down from the crowns while the leaves fall, in each
  player's own game: in the color that tree has that day, turning as they
  sink and lying still for a moment on the ground. Nothing is sent and
  nothing is stored, and outside the days when leaves fall nothing is
  looked at. New settings in `config/foliage-client.toml`, section
  `fallingLeaves`: `fallingLeaves`, `amount`, `distance`, `most`. The
  game's own particle setting is followed.
- Foliage tells Dynamic Trees what the season is: in winter a tree rests
  (no growth, no seeds, and fruit that still hangs withers), outside winter
  Dynamic Trees' own curves of the year decide, and where Seasons says
  there is no winter the trees grow and bear all year. New settings in
  `config/foliage-common.toml`, section `trees`: `seasonSource` and
  `growthInWinter`. Which seasons mod Dynamic Trees listens to stays its
  own choice (`preferredSeasonMod`).
- Fruit under a leaf spot that goes bare drops to the ground instead of
  hanging in a bare crown, and snow that lay on the crown falls down to
  the first block that can hold it, so the snow of a winter forest lies on
  the floor.
- `/foliage restore` gives a whole world its leaf blocks back, a few chunks
  per tick, including land nobody visits. It is the way to take the mod out
  of a world. Also `/foliage status` and `/foliage bare on|off`.
- Distant Horizons: far crowns keep their leaves all year, so a forest in
  the distance is whole instead of a patchwork of what someone happened to
  see in winter. Nothing of Distant Horizons is changed, and nothing it has
  stored is written.
- A round of the server over land that is already bare now counts the
  blocks of a chunk section in one pass instead of searching it, which is
  what the weeks of winter cost.
- Foliage reads the biome the same way everywhere now (as the game stores
  it, in cubes of 4 by 4 by 4 blocks), so a tree never rests in a wood
  whose leaves stay, and a worker thread of a server never waits for the
  server's own thread to hand it a chunk.

## 0.3.0

Real bare branches.

- While a leaf spot has no leaves (from "a few last leaves" in autumn to
  "first leaves" in spring), its leaf block is swapped for a bare block
  ("Bare Twigs"), and swapped back when the leaves return. For Dynamic
  Trees the bare block is a leaf of the same tree, so twigs hold and the
  tree keeps its shape. For everything else it is next to nothing: no
  collision, no outline, no shade, no drops. Fire eats it as slowly as
  wood and does not run along it as along leaves (settings `twigsBurn`
  and `twigsSpreadFire`).
- Light passes through bare crowns: the ground under a bare tree is as
  bright as open land, and the last leaves inside a half-bare crown are no
  longer drawn too dark. Rain and snow fall through, and snow that lay on a
  leaf goes with it.
- A tree that grows in winter grows bare. A bare tree that is cut down
  falls bare and drops nothing from its crown.
- Every plain kind of Dynamic Trees leaves (also those of addons) has a
  bare block, named `foliage:bare_<leaf block>`.
- The server swaps the blocks a little at a time, nearest trees first. New
  settings in `config/foliage-common.toml`, section `bareBranches`:
  `bareBlocks`, `chunksPerTick`, `changesPerTick`, `millisPerTick`,
  `twigsBurn`, `twigsSpreadFire`.
- **Players now need Foliage to join a server that has it.** Before the mod
  is taken out of a world, the bare blocks should be turned back into
  leaves: see "Taking the mod out of a world" in the README.
- Fixed: the log no longer gets a warning about a missing twig picture
  (`twigs_..._4`) for every kind of leaves at start.
- Not yet: snow on the branches of bare trees, falling leaves, a command to
  restore a whole world, a check with Distant Horizons.

## 0.2.0

The leaf calendar, as a look.

- Oak, birch and dark oak lose their leaves during the last 7 days of
  autumn and grow them back during the first 7 days of spring, wherever
  Seasons says there is a winter. Kinds start on different days, trees of
  one kind up to a day apart (close trees together), and a crown changes
  leaf block by leaf block: from its edge inward in autumn (birch from the
  twigs outward), from the twigs outward in spring.
- A leaf block goes through full, thin, thinner, a few last leaves and
  bare; in spring through buds, first leaves, half-grown leaves and full.
  Each stage has its own pieces and its own pictures, made from the leaf
  textures that are loaded.
- Bare trees are drawn as twigs on their branches: 0.7 to 0.9 faces per leaf
  block instead of 6. The costliest day of the year (new leaves in spring)
  needs 5.1 to 5.3.
- The rules of the leaf calendar are settings of the world in
  `config/foliage-common.toml` (12 of them, among them the list of kinds
  that shed). A server sends them to its players.
- New settings of the look in `config/foliage-client.toml`: which leaf
  spots of a bare tree carry a twig, and the sizes of the clumps of first
  and half-grown leaves.
- The numbers the pictures of the year are made by can be changed by a
  resource pack (field `pictures` of the atlas source).
- Needs Seasons 0.4.0. Seasons' own look of thinning trees is switched off
  for the leaves Foliage draws.
- Still only a look: the leaf blocks of a bare tree are in the world as
  before. Snow lies on them, they give shade and can be walked on, and the
  leaves left deep inside a half-bare crown are drawn too dark.

## 0.1.0

The first version: leaf shapes.

- The leaves of Dynamic Trees are drawn from outer faces, inner leaf planes,
  round leaf cards and small clumps, chosen by where a leaf block sits in
  the crown. Five shapes to choose from: plain, shell, bushy, tufts and
  mixed (the default).
- Leaf blocks buried in a crown are not drawn, and no face is drawn between
  two leaf blocks. No leaf block is drawn from more faces than in the
  unmodded game (6). Measured on full-grown trees standing alone: 3.5 to
  4.3 faces per leaf block for oak, birch, dark oak, jungle and cherry, 5.3
  to 5.6 for spruce.
- Leaf cards are made from the leaf textures that are loaded, so resource
  packs keep working.
- Settings in `config/foliage-client.toml`: the shape, a "fewest faces"
  switch, the most faces per leaf block, and the numbers the shapes are
  drawn by.
- While Seasons shows leaves thinning out (late autumn to early spring), the
  new shapes step aside and leaves look as they did before.
- Nothing in the world changes: only how leaves are drawn.
