# Changelog

All notable changes to Foliage, newest first. The version numbers are
explained in the README under "Versions".

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
