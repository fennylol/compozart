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
java Build.java dist      # dist/linux/compozart/ and dist/compozart-linux.tar.gz
```

`dist` builds the app folder for the OS it runs on, with Java included.
Build the Windows folder on Windows.

## Running

Unpack the archive and run `compozart/compozart` (`compozart.cmd` on Windows).

```
compozart/
  compozart        run script
  app/             compozart.jar and the bundled Java runtime
  settings/        settings.json, themes.json, icons.json, recent.json
    languages/     translations, plus en.template.json to start one
  compositions/    your projects, by default
    backups/       a copy on every save, and autosaves
```

The app opens on a home screen with your recent projects and everything in `compositions/`.
New projects save there by default; Save As can put them anywhere.

The settings files appear on first launch.
They are plain JSON, and the app picks up edits when its window regains focus.
`themes.json` holds the color themes (View > Theme switches them) and `icons.json` the toolbar icons and the app icon as 16×16 text grids.

To translate the app, copy `settings/languages/en.template.json` to a file named after the language code, such as `de.json`, and translate its values.
Shared words like "anchor" and "node" are defined once under `terms` and referenced as `{anchor}` or `{node:other}`, so each one is translated in one place.
Anything left untranslated falls back to English, and View > Language switches between the files.
German ships with the app as a complete example.

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
- New in the library adds a node, a folder, or an imported image. Rename (F2) works on both nodes and folders.
- Importing an image can add its colors to the palette or snap them to the colors already there.
- File nodes in folders to keep the library tidy. Drag nodes and folders around, or right-click for Move to. Folders never change how the creature is built.

Hover any tool or library icon for its name and key. The panel under the node library explains the current tool.
Every shortcut can be changed in Edit > Keyboard shortcuts, or in `settings.json` next to the program.
The app rereads that file when its window regains focus.

## Files

Projects save as `.zart` files, which are plain JSON.
Exports are a flat indexed PNG, a zip of indexed PNGs with one per layer, an indexed `.aseprite` file with one layer per creature layer, or a Godot 4 scene.
The Godot scene comes in 2D (`Sprite2D`) or 3D (`Sprite3D`). Each part sits on a pivot at its joint, so it can be animated by rotating that pivot.

Created with Claude Opus 5.5