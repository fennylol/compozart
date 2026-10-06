package compozart.ui;

import compozart.model.Project;

import java.util.ArrayDeque;
import java.util.Deque;

/** Undo and redo by whole-project snapshots. Projects are small, so this stays cheap and cannot miss a change. */
final class History {
    private static final int LIMIT = 200;

    private final Deque<Project> undo = new ArrayDeque<>();
    private final Deque<Project> redo = new ArrayDeque<>();
    private String lastKey;

    /**
     * Records the state before an edit. Consecutive edits with the same non-null key share one undo step,
     * so dragging a slider or a spinner does not flood the history.
     */
    void checkpoint(Project before, String coalesceKey) {
        if (coalesceKey != null && coalesceKey.equals(lastKey)) return;
        lastKey = coalesceKey;
        undo.push(before.copy());
        if (undo.size() > LIMIT) undo.removeLast();
        redo.clear();
    }

    /** Ends the current coalescing run, so the next edit gets its own undo step. */
    void breakCoalescing() {
        lastKey = null;
    }

    boolean canUndo() {
        return !undo.isEmpty();
    }

    boolean canRedo() {
        return !redo.isEmpty();
    }

    Project undo(Project current) {
        lastKey = null;
        redo.push(current);
        return undo.pop();
    }

    Project redo(Project current) {
        lastKey = null;
        undo.push(current);
        return redo.pop();
    }

    void clear() {
        undo.clear();
        redo.clear();
        lastKey = null;
    }
}
