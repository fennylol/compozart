package compozart.ui;

import compozart.compose.Composer;
import compozart.compose.Composition;
import compozart.compose.ParentGhost;
import compozart.model.Node;
import compozart.model.Project;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The editing session: the project, what is selected, the current tool, and undo history.
 * Views listen for changes and redraw.
 */
public final class Editor {
    public enum Change {
        /** Project data changed: pixels, anchors, nodes, palette, root or seed. */
        PROJECT,
        /** The project was replaced by undo, redo, new or open. */
        HISTORY,
        /** Selected node, anchor, color or parent background changed. */
        SELECTION,
        /** Tool, symmetry or tool option changed. */
        TOOL,
        /** File path or saved state changed. */
        FILE
    }

    public interface Listener {
        void changed(Set<Change> what);
    }

    public static final int NO_ANCHOR = -1;
    public static final int ROOT_ANCHOR = -2;

    private Project project;
    private final History history = new History();
    private Composition composition;
    private final List<Listener> listeners = new ArrayList<>();
    /** Called before anything that should see committed pixels: switching nodes or tools, saving, exporting. */
    private final List<Runnable> flushHooks = new ArrayList<>();

    private int nodeIndex;
    private int color = 1;
    private int anchor = NO_ANCHOR;
    /** 0 shows no parent; k shows candidate k - 1. */
    private int ghost;
    private Tool tool = Tool.DRAW;
    /** The tool to return to after the color picker picks a color. */
    private Tool beforePicker = Tool.DRAW;
    private int brushSize = 1;
    private Symmetry symmetry = Symmetry.NONE;
    private boolean rectFilled;
    private boolean grid = true;

    private Path file;
    private long revision, savedRevision;

    public Editor(Project project) {
        this.project = project;
    }

    // ---- listeners ----

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void addFlushHook(Runnable r) {
        flushHooks.add(r);
    }

    /** Commits any in-progress edit (a floating selection) to the project. */
    public void flush() {
        for (Runnable r : flushHooks) r.run();
    }

    private void fire(Set<Change> what) {
        for (Listener l : List.copyOf(listeners)) l.changed(what);
    }

    private void fire(Change first, Change... rest) {
        fire(EnumSet.of(first, rest));
    }

    // ---- project and composition ----

    public Project project() {
        return project;
    }

    public Composition composition() {
        if (composition == null) composition = Composer.compose(project);
        return composition;
    }

    /** Records an undo step before an edit. See {@link History#checkpoint}. */
    public void checkpoint(String coalesceKey) {
        history.checkpoint(project, coalesceKey);
    }

    public void breakCoalescing() {
        history.breakCoalescing();
    }

    /** Call after changing project data. */
    public void changed() {
        revision++;
        composition = null;
        clampSelection();
        fire(Change.PROJECT, Change.FILE);
    }

    /** Records an undo step, applies the edit, and notifies listeners. */
    public void edit(String coalesceKey, Runnable change) {
        checkpoint(coalesceKey);
        change.run();
        changed();
    }

    public boolean canUndo() {
        return history.canUndo();
    }

    public boolean canRedo() {
        return history.canRedo();
    }

    public void undo() {
        if (!history.canUndo()) return;
        project = history.undo(project);
        replaced();
    }

    public void redo() {
        if (!history.canRedo()) return;
        project = history.redo(project);
        replaced();
    }

    private void replaced() {
        revision++;
        composition = null;
        clampSelection();
        fire(EnumSet.allOf(Change.class));
    }

    /** Replaces the whole project, as after New or Open. Clears history. */
    public void load(Project p, Path file) {
        project = p;
        history.clear();
        this.file = file;
        nodeIndex = p.root != null && p.find(p.root) != null ? p.nodes.indexOf(p.find(p.root)) : 0;
        anchor = NO_ANCHOR;
        ghost = 0;
        color = Math.min(1, p.palette.size() - 1);
        revision = savedRevision = 0;
        composition = null;
        clampSelection();
        fire(EnumSet.allOf(Change.class));
    }

    public Path file() {
        return file;
    }

    public boolean dirty() {
        return revision != savedRevision;
    }

    /** Marks the project as changed since its last save, as when carrying unsaved work into a new window. */
    public void markDirty() {
        revision++;
        fire(Change.FILE);
    }

    public void markSaved(Path file) {
        this.file = file;
        savedRevision = revision;
        fire(Change.FILE);
    }

    // ---- selection ----

    private void clampSelection() {
        int n = project.nodes.size();
        if (nodeIndex >= n) nodeIndex = n - 1;
        if (nodeIndex < 0) nodeIndex = n == 0 ? -1 : 0;
        Node node = node();
        if (node == null || anchor >= node.anchors.size() || anchor == ROOT_ANCHOR && node.root == null) anchor = NO_ANCHOR;
        if (color >= project.palette.size()) color = project.palette.size() - 1;
        if (node == null) ghost = 0;
        else ghost = Math.min(ghost, ParentGhost.candidates(project, node).size());
    }

    /** The node being edited, or null when the library is empty. */
    public Node node() {
        return nodeIndex >= 0 && nodeIndex < project.nodes.size() ? project.nodes.get(nodeIndex) : null;
    }

    public int nodeIndex() {
        return nodeIndex;
    }

    public void selectNode(int index) {
        if (index == nodeIndex) return;
        flush();
        nodeIndex = index;
        anchor = NO_ANCHOR;
        ghost = 0;
        clampSelection();
        // Show the first parent automatically, so a newly opened part appears in place.
        Node n = node();
        if (n != null && !ParentGhost.candidates(project, n).isEmpty()) ghost = 1;
        fire(Change.SELECTION);
    }

    public void selectNode(Node n) {
        int i = project.nodes.indexOf(n);
        if (i >= 0) selectNode(i);
    }

    /** Moves through the nodes in the order the library shows them, folders included. */
    public void stepNode(int delta) {
        List<Node> order = project.libraryOrder();
        if (order.isEmpty()) return;
        int i = Math.max(0, order.indexOf(node()));
        selectNode(order.get(Math.floorMod(i + delta, order.size())));
    }

    public int color() {
        return color;
    }

    public void selectColor(int index) {
        if (index < 0 || index >= project.palette.size() || index == color) return;
        color = index;
        fire(Change.SELECTION);
    }

    public void stepColor(int delta) {
        selectColor(Math.floorMod(color + delta, project.palette.size()));
    }

    /** The selected anchor on the current node: an index, {@link #ROOT_ANCHOR} or {@link #NO_ANCHOR}. */
    public int anchor() {
        return anchor;
    }

    public void selectAnchor(int a) {
        if (a == anchor) return;
        anchor = a;
        clampSelection();
        fire(Change.SELECTION);
    }

    public List<ParentGhost.Candidate> ghostCandidates() {
        Node n = node();
        return n == null ? List.of() : ParentGhost.candidates(project, n);
    }

    /** The parent shown behind the canvas, or null. */
    public ParentGhost.Candidate ghost() {
        List<ParentGhost.Candidate> c = ghostCandidates();
        return ghost > 0 && ghost <= c.size() ? c.get(ghost - 1) : null;
    }

    public void stepGhost(int delta) {
        int n = ghostCandidates().size() + 1;
        ghost = Math.floorMod(ghost + delta, n);
        fire(Change.SELECTION);
    }

    // ---- tools ----

    public Tool tool() {
        return tool;
    }

    public void setTool(Tool t) {
        if (t == tool) return;
        flush();
        if (t == Tool.EYEDROPPER) beforePicker = tool;
        tool = t;
        fire(Change.TOOL);
    }

    /** Called when a color picker stroke ends having picked a color: returns to the tool used before it. */
    public void pickerDone() {
        if (tool == Tool.EYEDROPPER) setTool(beforePicker);
    }

    public int brushSize() {
        return brushSize;
    }

    public void setBrushSize(int size) {
        int s = Math.max(Brush.MIN, Math.min(Brush.MAX, size));
        if (s == brushSize) return;
        brushSize = s;
        fire(Change.TOOL);
    }

    public Symmetry symmetry() {
        return symmetry;
    }

    public void setSymmetry(Symmetry s) {
        symmetry = s;
        fire(Change.TOOL);
    }

    public boolean rectFilled() {
        return rectFilled;
    }

    public void setRectFilled(boolean f) {
        rectFilled = f;
        fire(Change.TOOL);
    }

    public boolean grid() {
        return grid;
    }

    public void setGrid(boolean g) {
        grid = g;
        fire(Change.TOOL);
    }
}
