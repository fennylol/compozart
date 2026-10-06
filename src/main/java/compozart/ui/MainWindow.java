package compozart.ui;

import compozart.compose.Composition;
import compozart.compose.ParentGhost;
import compozart.io.Exporter;
import compozart.io.ProjectIO;
import compozart.model.Dir;
import compozart.model.Project;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The application window: tree and library on the left, canvas in the middle, render and palette on the right. */
public final class MainWindow extends JFrame {
    private final Editor ed;
    private final KeyMap keys = new KeyMap();
    private final Settings settings = new Settings();
    private final NodeActions nodes;
    private final AnchorMenus anchorMenus;
    private final CanvasView canvas;
    private final RenderView render;
    private final AnchorPanel anchorPanel;
    private final JLabel status = new JLabel(" ");
    private final JLabel parentLabel = new JLabel();
    private final Map<Tool, JToggleButton> toolButtons = new EnumMap<>(Tool.class);
    private final JComboBox<Symmetry> symmetry = new JComboBox<>(Symmetry.values());
    private final JCheckBox filled = new JCheckBox("Filled");
    private final JCheckBox grid = new JCheckBox("Grid", true);
    private final JSpinner brush = new JSpinner(new SpinnerNumberModel(1, Brush.MIN, Brush.MAX, 1));
    private Path lastDir;
    private int exportPadding = 0, exportScale = 1;
    private Exporter.Format exportFormat = Exporter.Format.ASEPRITE;

    public MainWindow(Project project, Path file) {
        super("compozart");
        // Split panes bind the arrow keys for every component inside them, which would swallow the
        // anchor-direction keys. Keep only their focus and resize keys.
        UIManager.put("SplitPane.ancestorInputMap", new UIDefaults.LazyInputMap(new Object[]{
                "F6", "toggleFocus", "F8", "startResize"}));
        ed = new Editor(project);
        nodes = new NodeActions(ed, this);
        anchorMenus = new AnchorMenus(ed, nodes::create);
        canvas = new CanvasView(ed);
        canvas.setMenus(anchorMenus);
        render = new RenderView(ed);
        anchorPanel = new AnchorPanel(ed, anchorMenus);
        canvas.onStatus(status::setText);
        canvas.onAnchorCreated(a -> anchorPanel.focusTarget());
        canvas.onFocusAnchorSettings(anchorPanel::focusSettings);

        defineActions();
        String keyError = settings.load(keys);
        setJMenuBar(menus());
        keys.install(getRootPane());

        JSplitPane leftBottom = split(JSplitPane.VERTICAL_SPLIT, new NodeLibrary(ed, nodes), anchorPanel, 0.5);
        JSplitPane left = split(JSplitPane.VERTICAL_SPLIT, new CreatureTree(ed, nodes, nodes::create), leftBottom, 0.38);
        JSplitPane right = split(JSplitPane.VERTICAL_SPLIT, render, new PalettePanel(ed), 0.5);
        JSplitPane centerRight = split(JSplitPane.HORIZONTAL_SPLIT, canvasPanel(), right, 0.62);
        JSplitPane main = split(JSplitPane.HORIZONTAL_SPLIT, left, centerRight, 0.2);
        left.setMinimumSize(new Dimension(220, 100));
        right.setMinimumSize(new Dimension(280, 100));

        status.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        status.setForeground(Draw.MUTED);
        getContentPane().add(main, BorderLayout.CENTER);
        getContentPane().add(status, BorderLayout.SOUTH);

        ed.addListener(what -> refreshChrome());
        ed.load(project, file);
        if (file != null) lastDir = file.toAbsolutePath().getParent();

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (confirmDiscard()) {
                    dispose();
                    System.exit(0);
                }
            }
        });
        setSize(1500, 920);
        setLocationRelativeTo(null);
        if (keyError != null) SwingUtilities.invokeLater(() -> status.setText(keyError));
        // Pick up edits made to settings.json in another program.
        addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                String msg = settings.reloadIfChanged(keys);
                if (msg != null) status.setText(msg);
            }
        });
    }

    private static JSplitPane split(int orientation, Component a, Component b, double weight) {
        JSplitPane s = new JSplitPane(orientation, a, b);
        s.setResizeWeight(weight);
        s.setContinuousLayout(true);
        s.setBorder(null);
        SwingUtilities.invokeLater(() -> s.setDividerLocation(weight));
        return s;
    }

    // ---- actions ----

    private void defineActions() {
        KeyStroke[] none = {};
        int[] toolKeys = {KeyEvent.VK_D, KeyEvent.VK_E, KeyEvent.VK_F, KeyEvent.VK_R, KeyEvent.VK_C, KeyEvent.VK_L, KeyEvent.VK_U, KeyEvent.VK_A};
        for (Tool t : Tool.values()) keys.define("tool." + t.id, "Tool: " + t.label.toLowerCase(Locale.ROOT), () -> ed.setTool(t), KeyMap.key(toolKeys[t.ordinal()]));

        keys.define("brush.smaller", "Smaller brush", () -> ed.setBrushSize(ed.brushSize() - 1),
                KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        keys.define("brush.larger", "Larger brush", () -> ed.setBrushSize(ed.brushSize() + 1),
                KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET, java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        keys.define("color.prev", "Previous color", () -> ed.stepColor(-1), KeyMap.key(KeyEvent.VK_COMMA));
        keys.define("color.next", "Next color", () -> ed.stepColor(1), KeyMap.key(KeyEvent.VK_PERIOD));
        keys.define("node.prev", "Previous node", () -> ed.stepNode(-1), KeyMap.key(KeyEvent.VK_SEMICOLON));
        keys.define("node.next", "Next node", () -> ed.stepNode(1), KeyMap.key(KeyEvent.VK_QUOTE));
        keys.define("parent.prev", "Previous parent background", () -> ed.stepGhost(-1), KeyMap.key(KeyEvent.VK_OPEN_BRACKET));
        keys.define("parent.next", "Next parent background", () -> ed.stepGhost(1), KeyMap.key(KeyEvent.VK_CLOSE_BRACKET));

        keys.define("edit.undo", "Undo", () -> {
            canvas.deselect();
            ed.undo();
        }, KeyMap.ctrl(KeyEvent.VK_Z));
        keys.define("edit.redo", "Redo", ed::redo, KeyMap.ctrlShift(KeyEvent.VK_Z), KeyMap.ctrl(KeyEvent.VK_Y));
        keys.define("edit.delete", "Delete selection or anchor", () -> {
            if (canvas.hasSelection()) canvas.deleteSelection();
            else anchorMenus.delete();
        }, () -> canvas.hasSelection() || anchorMenus.hasAnchor(), KeyMap.key(KeyEvent.VK_DELETE), KeyMap.key(KeyEvent.VK_BACK_SPACE));
        keys.define("edit.deselect", "Deselect", canvas::deselect, canvas::hasSelection, KeyMap.key(KeyEvent.VK_ESCAPE));
        keys.define("edit.selectAll", "Select all pixels", () -> {
            ed.setTool(Tool.SELECT);
            canvas.selectAll();
        }, KeyMap.ctrl(KeyEvent.VK_A));

        Dir[] dirs = {Dir.N, Dir.E, Dir.S, Dir.W};
        String[] dirNames = {"up", "right", "down", "left"};
        int[] arrowKeys = {KeyEvent.VK_UP, KeyEvent.VK_RIGHT, KeyEvent.VK_DOWN, KeyEvent.VK_LEFT};
        for (int i = 0; i < 4; i++) {
            Dir d = dirs[i];
            keys.define("anchor." + dirNames[i], "Point anchor " + dirNames[i], () -> anchorMenus.point(d),
                    () -> ed.tool() == Tool.ANCHOR && anchorMenus.hasAnchor(), KeyMap.key(arrowKeys[i]));
        }

        keys.define("file.new", "New project", this::newProject, KeyMap.ctrl(KeyEvent.VK_N));
        keys.define("file.open", "Open…", this::open, KeyMap.ctrl(KeyEvent.VK_O));
        keys.define("file.save", "Save", this::save, KeyMap.ctrl(KeyEvent.VK_S));
        keys.define("file.saveAs", "Save as…", this::saveAs, KeyMap.ctrlShift(KeyEvent.VK_S));
        keys.define("file.export", "Export…", this::export, KeyMap.ctrl(KeyEvent.VK_E));
        keys.define("file.quit", "Quit", () -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING)), KeyMap.ctrl(KeyEvent.VK_Q));

        keys.define("node.new", "New node…", () -> nodes.create(null), KeyMap.shift(KeyEvent.VK_A));
        keys.define("node.duplicate", "Duplicate as new variant", nodes::duplicate, KeyMap.shift(KeyEvent.VK_D));
        keys.define("node.edit", "Edit name and variant…", nodes::edit, none);
        keys.define("node.resize", "Resize node…", nodes::resize, KeyMap.ctrl(KeyEvent.VK_R));
        keys.define("node.root", "Set node as root", () -> nodes.setRoot(ed.node()), none);
        keys.define("node.delete", "Delete node", nodes::delete, none);

        keys.define("view.zoomIn", "Zoom in", () -> canvas.zoomStep(1), KeyMap.key(KeyEvent.VK_EQUALS));
        keys.define("view.zoomOut", "Zoom out", () -> canvas.zoomStep(-1), KeyMap.key(KeyEvent.VK_MINUS));
        keys.define("view.fit", "Fit canvas", canvas::fit, KeyMap.key(KeyEvent.VK_0));
        keys.define("view.grid", "Toggle grid", () -> ed.setGrid(!ed.grid()), none);
        keys.define("view.symmetry", "Next symmetry mode", () -> {
            Symmetry[] all = Symmetry.values();
            ed.setSymmetry(all[(ed.symmetry().ordinal() + 1) % all.length]);
        }, none);
        keys.define("view.rectFill", "Toggle filled rectangle", () -> ed.setRectFilled(!ed.rectFilled()), none);
        keys.define("render.reroll", "Reroll random variants", render::reroll, none);
        keys.define("render.fit", "Fit render", render::fit, none);
        keys.define("keys.edit", "Keyboard shortcuts…", this::editKeys, none);
    }

    private JMenuBar menus() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");
        for (String id : List.of("file.new", "file.open", "file.save", "file.saveAs")) file.add(keys.menuItem(id));
        file.addSeparator();
        file.add(keys.menuItem("file.export"));
        file.addSeparator();
        file.add(keys.menuItem("file.quit"));
        bar.add(file);

        JMenu edit = new JMenu("Edit");
        for (String id : List.of("edit.undo", "edit.redo")) edit.add(keys.menuItem(id));
        edit.addSeparator();
        for (String id : List.of("edit.selectAll", "edit.deselect", "edit.delete")) edit.add(keys.menuItem(id));
        edit.addSeparator();
        edit.add(keys.menuItem("keys.edit"));
        bar.add(edit);

        JMenu node = new JMenu("Node");
        for (String id : List.of("node.new", "node.duplicate", "node.edit", "node.resize", "node.root")) node.add(keys.menuItem(id));
        node.addSeparator();
        for (String id : List.of("node.prev", "node.next", "parent.prev", "parent.next")) node.add(keys.menuItem(id));
        node.addSeparator();
        node.add(keys.menuItem("node.delete"));
        bar.add(node);

        JMenu tools = new JMenu("Tools");
        for (Tool t : Tool.values()) tools.add(keys.menuItem("tool." + t.id));
        tools.addSeparator();
        for (String id : List.of("brush.smaller", "brush.larger", "color.prev", "color.next", "view.symmetry", "view.rectFill")) {
            tools.add(keys.menuItem(id));
        }
        bar.add(tools);

        JMenu view = new JMenu("View");
        for (String id : List.of("view.zoomIn", "view.zoomOut", "view.fit", "view.grid")) view.add(keys.menuItem(id));
        view.addSeparator();
        for (String id : List.of("render.fit", "render.reroll")) view.add(keys.menuItem(id));
        bar.add(view);
        return bar;
    }

    private JComponent canvasPanel() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        ButtonGroup bg = new ButtonGroup();
        for (Tool t : Tool.values()) {
            JToggleButton b = new JToggleButton(t.label);
            b.setFocusable(false);
            b.addActionListener(e -> ed.setTool(t));
            bg.add(b);
            toolButtons.put(t, b);
            bar.add(b);
        }
        bar.addSeparator();
        bar.add(new JLabel(" Size "));
        brush.setToolTipText("Brush size for Draw and Eraser. Ctrl+wheel over the canvas also changes it.");
        brush.setMaximumSize(brush.getPreferredSize());
        brush.addChangeListener(e -> ed.setBrushSize((Integer) brush.getValue()));
        bar.add(brush);
        JToolBar parentBar = new JToolBar();
        parentBar.setFloatable(false);
        JButton prev = new JButton("◀");
        prev.setFocusable(false);
        prev.addActionListener(e -> ed.stepGhost(-1));
        JButton next = new JButton("▶");
        next.setFocusable(false);
        next.addActionListener(e -> ed.stepGhost(1));
        // Fixed-width controls first, so the parent label's changing length does not move them.
        parentBar.add(new JLabel(" Symmetry "));
        symmetry.setFocusable(false);
        symmetry.setMaximumSize(symmetry.getPreferredSize());
        symmetry.addActionListener(e -> ed.setSymmetry((Symmetry) symmetry.getSelectedItem()));
        parentBar.add(symmetry);
        filled.setFocusable(false);
        filled.addActionListener(e -> ed.setRectFilled(filled.isSelected()));
        parentBar.add(filled);
        grid.setFocusable(false);
        grid.addActionListener(e -> ed.setGrid(grid.isSelected()));
        parentBar.add(grid);
        parentBar.addSeparator();
        parentBar.add(new JLabel(" Parent background "));
        parentBar.add(prev);
        parentBar.add(next);
        parentBar.add(Box.createHorizontalStrut(6));
        parentBar.add(parentLabel);
        parentLabel.setForeground(Draw.MUTED);

        JPanel top = new JPanel(new BorderLayout());
        top.add(bar, BorderLayout.NORTH);
        top.add(parentBar, BorderLayout.SOUTH);
        JPanel p = new JPanel(new BorderLayout());
        p.add(top, BorderLayout.NORTH);
        p.add(canvas, BorderLayout.CENTER);
        return p;
    }

    private void refreshChrome() {
        JToggleButton b = toolButtons.get(ed.tool());
        if (b != null && !b.isSelected()) b.setSelected(true);
        if (symmetry.getSelectedItem() != ed.symmetry()) symmetry.setSelectedItem(ed.symmetry());
        if ((Integer) brush.getValue() != ed.brushSize()) brush.setValue(ed.brushSize());
        brush.setEnabled(ed.tool().brushed());
        filled.setSelected(ed.rectFilled());
        filled.setEnabled(ed.tool() == Tool.RECT);
        grid.setSelected(ed.grid());
        List<ParentGhost.Candidate> cands = ed.ghostCandidates();
        ParentGhost.Candidate g = ed.ghost();
        if (ed.node() != null && ed.node().root == null) parentLabel.setText("this node has no root anchor");
        else if (cands.isEmpty()) parentLabel.setText("no parent attaches this node");
        else if (g == null) parentLabel.setText("none (" + cands.size() + " available)");
        else parentLabel.setText(g.label() + "  (" + (cands.indexOf(g) + 1) + " of " + cands.size() + ")");
        String name = ed.file() == null ? "untitled" : ed.file().getFileName().toString();
        setTitle((ed.dirty() ? "*" : "") + name + " - compozart");
    }

    // ---- files ----

    private boolean confirmDiscard() {
        ed.flush();
        if (!ed.dirty()) return true;
        int r = JOptionPane.showConfirmDialog(this, "Save changes first?", "Unsaved changes", JOptionPane.YES_NO_CANCEL_OPTION);
        if (r == JOptionPane.YES_OPTION) return save();
        return r == JOptionPane.NO_OPTION;
    }

    private void newProject() {
        if (!confirmDiscard()) return;
        ed.load(Project.createDefault(), null);
    }

    private void open() {
        if (!confirmDiscard()) return;
        Path f = chooseFile("Open project", FileDialog.LOAD, null, ProjectIO.EXTENSION, ProjectIO.LEGACY_EXTENSION);
        if (f != null) openFile(f);
    }

    public void openFile(Path f) {
        try {
            Project p = ProjectIO.load(f);
            ed.load(p, f);
            lastDir = f.toAbsolutePath().getParent();
        } catch (IOException | RuntimeException ex) {
            JOptionPane.showMessageDialog(this, "Could not open " + f.getFileName() + ":\n" + ex.getMessage(),
                    "Open", JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean save() {
        // A file from before the rename is saved as a new .zart file rather than overwritten.
        if (ed.file() == null || ed.file().getFileName().toString().endsWith(ProjectIO.LEGACY_EXTENSION)) return saveAs();
        return writeProject(ed.file());
    }

    private boolean saveAs() {
        String suggested = (ed.file() != null ? baseName(ed.file()) : "creature") + ProjectIO.EXTENSION;
        Path f = chooseFile("Save project", FileDialog.SAVE, suggested, ProjectIO.EXTENSION);
        return f != null && writeProject(f);
    }

    private boolean writeProject(Path f) {
        ed.flush();
        try {
            ProjectIO.save(ed.project(), f);
            ed.markSaved(f);
            lastDir = f.toAbsolutePath().getParent();
            status.setText("Saved " + f);
            return true;
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not save:\n" + ex.getMessage(), "Save", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    /**
     * Shows the platform's native file dialog, listing files with any of the given extensions.
     * When saving, a name without the first extension gets it added.
     */
    private Path chooseFile(String title, int mode, String suggested, String... extensions) {
        String extension = extensions[0];
        FileDialog fd = new FileDialog(this, title, mode);
        if (lastDir != null) fd.setDirectory(lastDir.toString());
        fd.setFilenameFilter((dir, name) -> java.util.Arrays.stream(extensions).anyMatch(name::endsWith)
                || new java.io.File(dir, name).isDirectory());
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        if (suggested != null) fd.setFile(suggested);
        else if (windows) fd.setFile(String.join(";", java.util.Arrays.stream(extensions).map(e -> "*" + e).toList()));
        fd.setVisible(true);
        if (fd.getFile() == null) return null;
        Path f = Path.of(fd.getDirectory(), fd.getFile());
        if (mode == FileDialog.SAVE && !f.getFileName().toString().endsWith(extension)) {
            String n = f.getFileName().toString();
            if (extension.equals(ProjectIO.EXTENSION)) n = stripProjectExtension(n);
            f = f.resolveSibling(n + extension);
            if (Files.exists(f)) {
                int r = JOptionPane.showConfirmDialog(this, f.getFileName() + " already exists. Replace it?", title,
                        JOptionPane.OK_CANCEL_OPTION);
                if (r != JOptionPane.OK_OPTION) return null;
            }
        }
        return f;
    }

    /** A project file's name without its extension. */
    private static String baseName(Path file) {
        return stripProjectExtension(file.getFileName().toString());
    }

    private static String stripProjectExtension(String name) {
        for (String ext : List.of(ProjectIO.EXTENSION, ProjectIO.LEGACY_EXTENSION, ".json")) {
            if (name.endsWith(ext)) return name.substring(0, name.length() - ext.length());
        }
        return name;
    }

    // ---- export ----

    private void export() {
        ed.flush();
        Composition c = ed.composition();
        if (c.isEmpty()) {
            JOptionPane.showMessageDialog(this, "There is nothing to export: the creature has no pixels.", "Export",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JComboBox<Exporter.Format> format = new JComboBox<>(Exporter.Format.values());
        format.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                super.getListCellRendererComponent(l, v, i, s, f);
                Exporter.Format fm = (Exporter.Format) v;
                setText(fm.label + " (" + fm.extension + ")");
                return this;
            }
        });
        format.setSelectedItem(exportFormat);
        JSpinner padding = new JSpinner(new SpinnerNumberModel(exportPadding, 0, 256, 1));
        JSpinner scale = new JSpinner(new SpinnerNumberModel(exportScale, 1, 64, 1));
        JLabel summary = new JLabel();
        summary.setForeground(Draw.MUTED);
        Runnable update = () -> {
            Exporter.Format fm = (Exporter.Format) format.getSelectedItem();
            scale.setEnabled(fm.scalable());
            int pad = (Integer) padding.getValue(), sc = fm.scalable() ? (Integer) scale.getValue() : 1;
            int w = (c.width + 2 * pad) * sc, h = (c.height + 2 * pad) * sc;
            String layers = fm == Exporter.Format.FLAT_PNG ? "1 image" : c.layers.size() + (c.layers.size() == 1 ? " layer" : " layers");
            summary.setText(w + "×" + h + " · " + layers + " · " + ed.project().palette.size() + " colors");
        };
        format.addActionListener(e -> update.run());
        padding.addChangeListener(e -> update.run());
        scale.addChangeListener(e -> update.run());
        update.run();
        JPanel form = NodeActions.form(new String[]{"Format", "Padding", "Scale", ""}, format, padding, scale, summary);
        if (!c.warnings.isEmpty()) {
            JLabel warn = new JLabel("<html>" + String.join("<br>", c.warnings) + "</html>");
            warn.setForeground(Draw.WARNING);
            form.add(warn, NodeActions.gbc(4, 0, 2));
        }
        int r = JOptionPane.showConfirmDialog(this, form, "Export", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;
        exportFormat = (Exporter.Format) format.getSelectedItem();
        exportPadding = (Integer) padding.getValue();
        exportScale = (Integer) scale.getValue();

        String base = ed.file() == null ? "creature" : baseName(ed.file());
        Path f = chooseFile("Export " + exportFormat.label, FileDialog.SAVE, base + exportFormat.extension, exportFormat.extension);
        if (f == null) return;
        try {
            byte[] data = Exporter.export(exportFormat, c, ed.project().palette, exportPadding, exportFormat.scalable() ? exportScale : 1);
            Files.write(f, data);
            status.setText("Exported " + f);
        } catch (IOException | RuntimeException ex) {
            JOptionPane.showMessageDialog(this, "Could not export:\n" + ex.getMessage(), "Export", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---- keys ----

    private void editKeys() {
        Map<String, List<KeyStroke>> next = new KeyBindingsDialog(this, keys).showDialog();
        if (next == null) return;
        keys.setBindings(next);
        try {
            status.setText("Saved shortcuts to " + settings.save(keys));
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not save shortcuts:\n" + ex.getMessage(), "Shortcuts",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}
