# Changelog

All notable changes to Foliage, newest first. The version numbers are
explained in the README under "Versions".

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
