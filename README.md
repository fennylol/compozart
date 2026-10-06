# critter_inator

*A pixel art creature composer that builds each creature from a tree of **nodes** joined at **anchors**.*

Each node is a small square canvas with a root anchor (the plug) and any number of named anchors (the sockets).
A node named "eye" attaches to every anchor named "eye", rotated and mirrored to fit.
[SPEC.md](SPEC.md) describes the behavior in full.

## Building

Building needs a JDK 21.
On Linux that is `openjdk-21-jdk`.
There is no Gradle or Maven: the build is one Java file.

```
java Build.java run       # compile and start
java Build.java test      # run the tests
java Build.java jar       # dist/critter_inator.jar, needs Java 21+ to run
java Build.java bundle    # dist/critter_inator-linux.tar.gz, Java included
```

The bundle only runs on the OS that built it.
Build the Windows bundle on Windows.

## Running

```
java -jar dist/critter_inator.jar examples/demo.critter.json
```

From the bundle, run `critter_inator/bin/critter_inator`.
The demo has a body, a pair of eyes in a random group, mirrored arms behind the body, and a tail repeated four times with a tip.

## Working with it

The left column holds the creature tree, the node library, and the selected anchor's properties.
The canvas edits one node.
The right column shows the composed render and the palette.

- Draw with the pixel tools. Right-click paints clear, Alt+click picks a color.
- Press `A` for the anchor tool. Click to add a named anchor, Shift+click to place the root anchor, arrow keys to point it.
- Type the anchor's target in the properties panel. Double-click an anchor to open or create the node it attaches.
- Use `[` and `]` to show a parent faded behind the node, placed through the anchor.
- Duplicate a node to make a variant. Anchors pick a random variant unless one is pinned.

Every shortcut can be changed in Edit > Keyboard shortcuts.

## Files

Projects save as `.critter.json`.
Exports are a flat indexed PNG, a zip of indexed PNGs with one per layer, or an indexed `.aseprite` file with one layer per creature layer.
