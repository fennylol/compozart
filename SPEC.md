# compozart

*A pixel art creature composer that builds each creature from a tree of **nodes** joined at **anchors**.*

Each node is a small square pixel canvas.
A node attaches to a parent by matching its root anchor to a named anchor on the parent, the way a Mr. Potato Head part plugs into a hole.
The tool has three views (canvas, tree, render), one shared indexed palette, and exports to Aseprite and indexed PNG.

This document has two parts.
The requirements describe what the app does and should hold regardless of implementation.
The stack describes how it is built today and may change.

# Requirements

## Conventions

- Pixel coordinates are integers with `(0, 0)` at the top-left pixel, x to the right, y down.
- Directions are the four cardinal directions: N `(0, -1)`, E `(1, 0)`, S `(0, 1)`, W `(-1, 0)`.
- All placement transforms are lossless: 90° rotations, reflections, and integer translations.
- 45° rotation is out of scope, since no 45° rotation maps a pixel grid onto itself.

## Project

A project holds:

- one palette, shared by every node
- a library of nodes
- the root selection, which names the node at the top of the creature
- a random seed, used to resolve unpinned variants
- a format version, for migrating old save files

A project describes one creature.

## Palette

- Every pixel stores a palette index, never a color.
- Index 0 is always fully clear. It cannot be edited, moved, or deleted.
- The palette holds at most 256 entries, including index 0.
- Each entry has an RGBA color and an optional name.
- Entries may be partly transparent.
- New projects start with index 0 followed by the 26 Catppuccin Mocha colors, in the order listed below, with their Catppuccin names.
- Editing an entry recolors every pixel that uses it, in every node.
- Reordering entries moves the pixels with them, so no node changes appearance.
- Deleting an entry that is in use asks which index those pixels should move to.

### Color editor

- Primary controls are OKLCH sliders: lightness, chroma, hue.
- A toggle switches the sliders to OKLab: L, a, b.
- An alpha slider sits below either set.
- A hex field accepts `#RRGGBB` or `#RRGGBBAA`.
- Colors outside sRGB are brought into gamut by reducing chroma at fixed lightness and hue, and the editor shows a warning while that is happening.

### Catppuccin Mocha

| Index | Name | Hex |
|---|---|---|
| 1 | rosewater | `#f5e0dc` |
| 2 | flamingo | `#f2cdcd` |
| 3 | pink | `#f5c2e7` |
| 4 | mauve | `#cba6f7` |
| 5 | red | `#f38ba8` |
| 6 | maroon | `#eba0ac` |
| 7 | peach | `#fab387` |
| 8 | yellow | `#f9e2af` |
| 9 | green | `#a6e3a1` |
| 10 | teal | `#94e2d5` |
| 11 | sky | `#89dceb` |
| 12 | sapphire | `#74c7ec` |
| 13 | blue | `#89b4fa` |
| 14 | lavender | `#b4befe` |
| 15 | text | `#cdd6f4` |
| 16 | subtext1 | `#bac2de` |
| 17 | subtext0 | `#a6adc8` |
| 18 | overlay2 | `#9399b2` |
| 19 | overlay1 | `#7f849c` |
| 20 | overlay0 | `#6c7086` |
| 21 | surface2 | `#585b70` |
| 22 | surface1 | `#45475a` |
| 23 | surface0 | `#313244` |
| 24 | base | `#1e1e2e` |
| 25 | mantle | `#181825` |
| 26 | crust | `#11111b` |

## Nodes

- A node has a name, an optional variant name, a size, a pixel grid, at most one root anchor, and an ordered list of named anchors.
- The pair of name and variant name identifies a node, and must be unique in the project. A blank variant name counts as a value, so each name has at most one node with a blank variant.
- Nodes are always square. The default size is 32×32, and any size from 1 to 256 is allowed.
- A node does not have to fill its canvas. Unused pixels stay clear.
- A node without a root anchor can only be used as the creature root.

### Resizing

- Resizing opens a dialog with a 3×3 grid that picks which part of the canvas stays fixed, as in Aseprite's canvas size dialog.
- Pixels and anchors shift with the content.
- Pixels that fall outside the new size are discarded.
- Anchors that fall outside are removed, and the dialog warns before applying.

## Anchors

An anchor is a pixel position and a direction.
It sits on the center of its pixel and points at one of that pixel's four edges.
That edge is where two nodes fuse.
It can be anywhere on the canvas, including the middle.

### Root anchor

- A node's root anchor is the plug that attaches it to a parent.
- It has a position and a direction, and nothing else.

### Named anchors

A named anchor is a socket.
It has:

| Field | Meaning | Default |
|---|---|---|
| target name | which node name attaches here | required |
| position, direction | where the child fuses | required |
| variant | which variant of the target to attach, or random | random |
| random group | random anchors that share a group share one pick | blank |
| layer modifier | the child's layer relative to this node's layer, any integer | `+1` |
| mirrored | reflect the child and its whole subtree | off |
| depth | how many times a repeating node appears below this anchor | see [Cycles and depth](#cycles-and-depth) |
| end variant | the variant used for the last repetition | none |

- An anchor's variant is either random or a specific variant. The unnamed variant counts as a specific variant, so it can be pinned like any other.
- Several named anchors on one node may share a target name, for example two "eyes" sockets on one head.
- The order of the anchor list is editable and decides draw order between children on the same layer.

### Drawing anchors

- Anchors are drawn as arrows from the pixel center to the target edge.
- Each arrow also extends backward past its pixel, so a small anchor stays easy to find. Clicking anywhere on the drawn arrow selects it.
- Root and named anchors use different colors.
- The selected anchor is highlighted, and its fields appear in a properties panel.

## Composition

### Placement

The root is placed at the origin with no rotation or reflection.
Every other node instance is placed from its parent as follows.

The parent's named anchor and the child's root anchor point into each other across a single pixel edge.
The child's root pixel lands on the pixel just past the edge the parent's arrow points at.
The two anchor pixels are neighbors and never overlap.

Formally, each instance has a world transform `p ↦ R·p + t`, where `R` is one of the 8 symmetries of the square and `t` is an integer offset.
For a parent anchor with world pixel `P` and world direction `D`, and a child root anchor at local pixel `q` with local direction `e`:

- The child's handedness is the parent's handedness, flipped if the anchor is mirrored.
- `R_child` is the one symmetry with that handedness that maps `e` to `-D`.
- `t_child = P + D - R_child·q`.

Two consequences follow.
Mirroring reflects the child across its own root arrow, so the root anchor stays put.
A mirror on a mirrored parent cancels out, and grandchildren inherit their parent's handedness.

A left arm is therefore the right arm attached through an anchor pointing the other way with mirrored on.

### Layers

- The root is on layer 0.
- A child's layer is its parent's layer plus the anchor's layer modifier.
- Layers can be negative.
- Each subtree is placed as a unit, but drawing happens layer by layer across the whole creature, so a child can sit behind its parent while a grandchild sits in front.

### Draw order

- Layers draw from lowest to highest.
- Within a layer, instances draw in depth-first tree order: a parent before its children, and children in the order of the parent's anchor list.
- Later draws go on top.

### Pixel compositing

- A non-clear pixel replaces whatever is beneath it, whatever its alpha.
- Clear pixels never overwrite anything.
- This keeps every composed pixel an exact palette index.
- The render view and the flat PNG export both follow this rule.
- The layered exports keep alpha per layer, so blending between layers happens in whatever program opens them.

### Variants

For a named anchor targeting name `T`:

- With a variant set, the node `(T, variant)` attaches. If it does not exist, nothing attaches and the tree view flags the anchor.
- With a random variant and no random group, one node named `T` is picked for each anchor instance.
- With a random variant and a random group `G`, every anchor instance targeting `T` with group `G` shares a single pick.
- Random picks only consider nodes that have a root anchor.
- Inside a repetition with an end variant, random picks skip the end variant, so a tail tip never appears mid-tail.
- If no node is named `T`, or the anchor has no target, nothing attaches. The tree view shows the anchor as empty.

Random picks are deterministic.
Each pick comes from hashing the project seed with the anchor instance's path from the root (or with `T` and `G` for a group).
The same project and seed always produce the same creature, and editing one part does not reshuffle unrelated parts.
A reroll button replaces the seed.

### Cycles and depth

Names form a graph: node name `A` points at name `B` when some node named `A` has an anchor targeting `B`.
Cycles are allowed, for example a "tail" node with its own "tail" anchor.

- Any anchor whose target is on a cycle has a depth field.
- On an anchor outside the cycle, depth is required. This is the anchor that starts the repetition, such as a body's "tail" anchor with depth 5.
- Depth counts how many instances of the target name appear along that branch. Depth 5 gives 5 tail segments. Depth 0 attaches nothing.
- A missing required depth is treated as 1, with a warning.
- Anchors inside the cycle continue the count they inherit and ignore their own depth field.
- An in-cycle anchor uses its own depth only when no count is active, for example when the repeating node is the creature root. Its default is 1.
- In a cycle through several names, only the name the count started on is counted. For `a → b → a` started at `a` with depth 3, the branch gets 3 of each.
- A loop inside the cycle that never passes back through the counted name runs once, then stops.
- If the starting anchor has an end variant, the last instance uses it, for example a tail tip.
- A node with two in-cycle anchors branches, and each branch keeps its own count.
- Composition stops at 4096 instances and the render view shows a warning.

## Views

The window shows the tree on the left, the canvas in the center, the render on the right, and the palette along one edge.
The dividers between them can be dragged.

### Canvas view

The canvas view edits the selected node.

- Integer zoom, pan, and an optional pixel grid.
- Tools: draw, eraser, fill (4-connected), rectangle select and move, eyedropper, line, rectangle (outline or filled), anchor.
- Draw and eraser use a round brush, 1 to 32 pixels across. Size 1 is one pixel, 2 a square, 3 a plus; larger sizes are circles. Even sizes lean up and left of the cursor. A preview follows the cursor, mirrored by the symmetry mode and limited to pixels that can be painted. Draw previews in the selected color; the eraser, or the clear color, shows only the shape. A dark and light outline keeps the preview visible over any color, including low-alpha colors and colors that match the pixels underneath.
- While a selection exists, draw, eraser, fill, line and rectangle only change pixels inside it, and fill stops at its edge. Anchors are not affected. Deselecting lifts the restriction.
- Right-click paints clear with the line, rectangle and fill tools. Alt+click picks a color with any drawing tool.
- The eraser's left button erases pixels only. Its right button erases anchors, the same way as the anchor tool's right button.
- With the draw tool, right-click adds an anchor and Shift+right-click places the root anchor. Right-clicking a pixel that already has an anchor opens that anchor's menu.
- A moved selection floats until you deselect, switch tools or switch nodes. Clear pixels in it do not overwrite.
- Ctrl+dragging a selection moves a copy and leaves the original in place. Ctrl+dragging a floating selection stamps it where it is and drags off another copy. Undo returns to before the first lift.
- Anchor tool: click an empty pixel to add a named anchor, Shift+click to place or move the root anchor. Press on an existing anchor and drag to move it. Arrow keys point the selected anchor and Delete removes it. Right-click erases the anchor under the cursor; right-dragging erases every anchor along the path, as one undo step.
- Adding an anchor, with either tool, is a press and drag. The anchor sits on the pixel where the press started and points toward the cursor, in whichever of the four directions is closest. Releasing without leaving the pixel keeps the default: up for a named anchor, the previous direction (or down) for the root anchor. Adding and aiming are one undo step.
- Symmetry modes for the drawing tools: none, left/right, top/bottom, quad, diagonal `\`, diagonal `/`. The mirror axes go through the canvas center.
- Double-clicking an anchor with the anchor tool selects it and moves the keyboard into its settings: the target name for a named anchor, the direction buttons for the root anchor.
- The anchor panel's Open button, and the draw tool's right-click menu on an anchor, open the node that attaches there. If more than one variant could attach, a menu lists them, with an option to create a new one.

#### Parent background

- The canvas can show a faded parent behind the node being edited, so a part can be drawn in place.
- The candidates are every pair of a parent node and one of its anchors that targets this node's name.
- The previous/next parent keys cycle through the candidates and an empty option.
- Switching to a node shows its first candidate automatically.
- The parent is drawn in the edited node's frame: rotated and mirrored as it would appear relative to this node, with the node itself fixed.

### Tree view

The tree view has two sections.

The creature tree shows the expanded instance tree from the root:

- Each row shows the node name, the resolved variant, and the layer.
- Selecting a row selects that node for the canvas view.
- Mirrored and layer modifier can be edited from the row.
- Empty or unresolved anchors are shown as rows of their own.
- Any node can be made the root from its context menu.

The node library lists every node, attached or not, filed in folders:

- Create, rename, duplicate, delete, set variant name, resize, reorder.
- Folders nest to any depth. Each folder shows how many nodes it holds, counting subfolders.
- Folders only organize the library. Anchors match nodes by name wherever they are filed, so refiling a node never changes the creature.
- A folder lists its subfolders first, in name order, then its own nodes in the order you set. Moving a node up or down stays within its folder.
- New nodes and folders go into the selected folder, or the selected node's folder.
- Drag a node or folder onto a folder to move it there, or onto empty space to move it to the top level. Right-click offers the same as a Move to menu.
- Renaming a folder carries its contents along. Moving a folder where one of the same name exists merges the two.
- Deleting a folder keeps its contents: its nodes and subfolders move up one level.
- New nodes get a root anchor on the middle pixel, pointing down, unless that option is turned off. On even sizes the middle leans toward (0, 0): pixel (15, 15) on a 32×32 node.
- Duplicating a node creates a new variant of the same name.
- Renaming a node offers to update the anchors that target the old name, when no other node keeps that name.
- The previous/next node keys move through the library in the order it is shown, folders included.

### Render view

- Shows the composed creature using the compositing rule above.
- Integer zoom and pan.
- Shows the current seed and has a reroll button.
- An optional overlay shows anchors.
- An optional outline marks every placement of the node being edited.
- Clicking a pixel selects the node that drew it.

## Editing

- Undo and redo cover every change: pixels, anchors, node operations, palette edits, root selection, seed.

## Keyboard

Every action below can be rebound in Edit > Keyboard shortcuts, or by editing `settings.json`.

### Settings file

- Settings live in `settings.json` next to the program: beside the self-extracting file, beside the bundle's launcher, or beside the jar. Running from compiled classes during development uses the working directory.
- It is plain JSON outside any archive, so it can be edited in any text editor.
- The first start writes a complete file with every action and its keys, defaults included.
- The app rereads the file whenever its window regains focus, so outside edits apply without a restart.
- Keys are written like `ctrl+shift+Z`. Key names are Java `KeyEvent` names without `VK_`, such as `COMMA` or `OPEN_BRACKET`. Single characters like `,` and `[` are also accepted. An empty list leaves an action unbound.
- Unknown actions or keys are skipped and reported in the status bar.
- The file has a version number. When a new version changes a default key, an older file that still holds the old default picks up the new one; keys the user changed stay as they are. When an action is renamed (the pencil became `tool.draw` in version 3), its saved keys move to the new name. The file is then rewritten at the new version.
- If the program's folder is not writable, settings go to `~/.config/compozart/` on Linux or `%APPDATA%\compozart\` on Windows.
Conflicting bindings are flagged in the dialog.
Plain keys are ignored while a text field has focus, so typing a name never switches tools.

| Action | Default |
|---|---|
| Draw | `D` |
| Eraser | `E` |
| Fill | `F` |
| Select | `R` |
| Eyedropper | `C` |
| Line | `L` |
| Rectangle | `U` |
| Anchor | `A` |
| Point the selected anchor | arrow keys |
| Delete selection or anchor | `Delete`, `Backspace` |
| Deselect | `Esc` |
| Select all pixels | `Ctrl+A` |
| New node | `Shift+A` |
| Duplicate node as a new variant | `Shift+D` |
| Resize node | `Ctrl+R` |
| Zoom in / out / fit | `=` / `-` / `0` |
| Smaller / larger brush | `Shift+[` / `Shift+]`, or Ctrl+wheel over the canvas |
| Previous / next color | `,` / `.` |
| Previous / next node | `;` / `'` |
| Previous / next parent background | `[` / `]` |
| Undo | `Ctrl+Z` |
| Redo | `Ctrl+Shift+Z`, `Ctrl+Y` |
| New / open / save / save as | `Ctrl+N` / `Ctrl+O` / `Ctrl+S` / `Ctrl+Shift+S` |
| Export | `Ctrl+E` |
| Quit | `Ctrl+Q` |

The other node commands, grid, symmetry, filled rectangle, reroll and render fit start unbound.

## Files

### Save file

- Projects save as JSON, with the extension `.zart`.
- Files from before the rename to compozart (`.critter.json`, format tag `critter_inator`) still open. Saving one asks for a new `.zart` name rather than overwriting it.
- The file holds the format version, palette, folders, nodes, anchors, root selection, and seed.
- Folders are stored as paths: a `folders` list (so empty folders survive) and a `folder` field on each node, such as `"head/eyes"`. An empty string is the top level. Files without these fields load with every node at the top level.
- Pixel grids are stored as one string per row, two hex digits per pixel, so the file stays readable and diffs cleanly.

### Exports

All exports crop to the bounding box of the composed creature, with optional padding.
PNG exports have an optional integer scale.

- **Flat PNG**: one indexed PNG of the whole creature. It carries the full palette, with alpha per entry.
- **Layer zip**: one indexed PNG per layer, named `layer_00.png`, `layer_01.png`, and so on, all the same size so they line up. Only non-empty layers are written.
- **Aseprite**: one `.aseprite` file in indexed color mode, with index 0 as the transparent color, the full palette, one frame, and one Aseprite layer per creature layer.

Layer numbers are shifted so the lowest layer becomes 0.
Everything on a given layer goes into the same exported layer, whatever node drew it.

## Later

- Posing, through per-anchor angle offsets.
- Animation.

# Stack

This section is expected to change.

## Language and UI

- Java 21.
- Swing for the UI.
- FlatLaf for the look and feel, dark by default. It is the only third-party library: one jar, Apache 2.0, kept in `lib/`.
- `java.awt.FileDialog` for open and save dialogs. It uses GTK on Linux and the native dialog on Windows. Where GTK is missing it falls back to a plain dialog that still works.
- Swing's `InputMap` and `ActionMap` for keybindings.

## Formats

All written by hand on top of the JDK:

- Indexed PNG encoder, using `java.util.zip.Deflater` and `CRC32`.
- Aseprite writer, following the published Aseprite file format.
- Zip export, using `java.util.zip`.
- JSON reader and writer for save files and keybindings.
- OKLab and OKLCH conversion.

## Build

The build is a single `Build.java` run with the JDK's source launcher.
There is no Gradle or Maven.

| Command | Result |
|---|---|
| `java Build.java compile` | Compiles the app |
| `java Build.java test` | Compiles and runs the tests with a small built-in test runner |
| `java Build.java run` | Compiles and starts the app |
| `java Build.java jar` | `dist/compozart.jar`, including FlatLaf |
| `java Build.java bundle` | `dist/<os>/compozart-<os>.tar.gz` (`.zip` on Windows), with a trimmed Java runtime |
| `java Build.java single` | `dist/<os>/compozart`, one self-extracting executable (Linux only for now) |

The jar runs on any OS, so it sits at the top of `dist/`.
Everything tied to one OS goes in `dist/<os>/`: `linux`, `windows` or `macos`.
| `java Build.java clean` | Removes build output |

## Distribution

- **Jar**: about 1.2 MB. It runs anywhere Java 21 or newer is installed.
- **Single file**: about 36 MB. A short shell header followed by the bundle as `.tar.gz`. The first run unpacks it to `~/.cache/compozart/<version>-<hash>/` (or under `$XDG_CACHE_HOME`), which takes a few seconds; later runs start from there. A rebuilt file has a new hash and unpacks fresh. Old unpacked versions are not cleaned up automatically. It needs only `sh`, `tar` and `gzip`, which every Linux desktop has.
- **Bundle**: about 36 MB compressed and 56 MB unpacked. It contains a native launcher and a trimmed runtime, built with `jlink` and `jpackage`, so the target machine needs no Java. The Linux bundle is a `.tar.gz` so the launcher keeps its executable bit. A bundle only runs on the OS it was built on, so the Windows bundle is built on Windows.

Building needs a JDK 21 with its `jmods` directory: `openjdk-21-jdk` on Linux, Temurin 21 on Windows.

## Layout

```
Build.java
SPEC.md
examples/            demo creature
lib/                 FlatLaf jar
src/main/java/       app source
src/test/java/       tests
build/               compiler output (generated)
dist/                jar (generated)
dist/<os>/           bundle and single file (generated)
```
