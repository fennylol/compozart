# compozart

*A pixel art composer that builds a drawing from a tree of **nodes** joined at **anchors**.*

Each node is a canvas with a root anchor (the plug) and any number of named anchors (the sockets).
A node named "eye" attaches to every anchor named "eye", rotated and mirrored to fit.
[SPEC.md](SPEC.md) describes the behavior in full.

## Building

Building needs a JDK 21.
On Linux that is `openjdk-21-jdk`.
There is no Gradle or Maven: the build is one Java file.

```
java Build.java run       # compile and start
java Build.java test      # run the tests
java Build.java jar       # dist/compozart.jar, needs Java 21+ to run
java Build.java bundle    # dist/linux/compozart-linux.tar.gz, Java included
java Build.java single    # dist/linux/compozart, one executable, Java included
```

The bundle and the single file only run on the OS that built them, and go in `dist/<os>/`.
Build the Windows bundle on Windows.
The single file is Linux only for now.

## Running

```
java -jar dist/compozart.jar examples/demo.zart
```

The single file runs directly: `dist/linux/compozart`.
Its first start unpacks into `~/.cache/compozart/` and takes a few seconds.
From the bundle, run `compozart/bin/compozart`.
The demo has a body, a pair of eyes in a random group, mirrored arms behind the body, and a tail repeated four times with a tip.

## Working with it

The left column holds the creature tree, the node library, and the selected anchor's properties.
The canvas edits one node.
The right column shows the composed render and the palette.

- Draw with the pixel tools. Alt+click picks a color. The Size box, `Shift+[` / `Shift+]` or Ctrl+wheel set the brush size for Draw and Eraser.
- While a selection is active, painting only touches pixels inside it. Esc deselects.
- Add an anchor by right-dragging with the draw tool, or dragging with the anchor tool (`A`). It sits where you pressed and points toward the cursor. Hold Shift for the root anchor.
- Right-click with the anchor tool or the eraser erases anchors. The eraser's left button only erases pixels.
- `Shift+A` adds a node, `Shift+D` duplicates the current one as a new variant, `Ctrl+R` resizes it.
- With the select tool, Ctrl+drag a selection to copy it instead of moving it.
- Double-click an anchor to jump to its settings and type its target. The panel's Open button opens or creates the node it attaches.
- Use `[` and `]` to show a parent faded behind the node, placed through the anchor.
- Duplicate a node to make a variant. Anchors pick a random variant unless one is pinned.
- File nodes in folders to keep the library tidy. Drag nodes and folders around, or right-click for Move to. Folders never change how the creature is built.

Every shortcut can be changed in Edit > Keyboard shortcuts, or in `settings.json` next to the program.
The app rereads that file when its window regains focus.

## Files

Projects save as `.zart` files, which are plain JSON.
Exports are a flat indexed PNG, a zip of indexed PNGs with one per layer, or an indexed `.aseprite` file with one layer per creature layer.

Created with Claude Opus 5.5