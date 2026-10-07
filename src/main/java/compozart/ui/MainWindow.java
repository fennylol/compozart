package compozart.ui;

import compozart.compose.Composition;
import compozart.compose.ParentGhost;
import compozart.io.Exporter;
import compozart.io.ProjectIO;
import compozart.model.Dir;
import compozart.model.Project;
import compozart.text.L10n;

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
    private final Backups backups = new Backups(AppHome.backupsDir());
    /** Autosaves unsaved work into the backups folder every few minutes. */
    private final javax.swing.Timer autosave = new javax.swing.Timer(60_000, e -> autosave());
    private final NodeActions nodes;
    private final AnchorMenus anchorMenus;
    private final CanvasView canvas;
    private final RenderView render;
    private final AnchorPanel anchorPanel;
    private NodeLibrary library;
    private final JLabel status = new JLabel(" ");
    private final JLabel parentLabel = new JLabel();
    private final Map<Tool, JToggleButton> toolButtons = new EnumMap<>(Tool.class);
    private final JComboBox<Symmetry> symmetry = new JComboBox<>(Symmetry.values());
    private final JCheckBox filled = new JCheckBox(L10n.t("toolbar.filled"));
    private final JCheckBox grid = new JCheckBox(L10n.t("toolbar.grid"), true);
    private final JSpinner brush = new JSpinner(new SpinnerNumberModel(1, Brush.MIN, Brush.MAX, 1));
    private Path lastDir;
    private final Recent recent = new Recent();
    private int exportPadding = 0, exportScale = 1;
    private double exportPixelSize = compozart.io.GodotScene.DEFAULT_PIXEL_SIZE;
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
        anchorPanel = new AnchorPanel(ed, anchorMenus, this::keyHint);
        canvas.onStatus(status::setText);
        canvas.onAnchorCreated(a -> anchorPanel.focusTarget());
        canvas.onFocusAnchorSettings(anchorPanel::focusSettings);

        defineActions();
        String keyError = settings.load(keys);
        setJMenuBar(menus());
        keys.install(getRootPane());

        library = new NodeLibrary(ed, nodes, this::keyHint);
        JSplitPane leftBottom = split(JSplitPane.VERTICAL_SPLIT, library, anchorPanel, 0.5);
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
        if (file != null) {
            lastDir = file.toAbsolutePath().getParent();
            recent.add(file, settings.recentProjects);
        }

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
        AppIcon.apply(this);
        setLocationRelativeTo(null);
        if (keyError != null) SwingUtilities.invokeLater(() -> status.setText(keyError));
        // Pick up edits made to settings.json in another program.
        addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                String msg = settings.reloadIfChanged(keys);
                if (msg != null) {
                    status.setText(msg);
                    anchorPanel.refreshHelp();
                    restartAutosave();
                }
                String look = Appearance.get().reloadIfChanged();
                if (look != null) status.setText(look);
            }
        });
        restartAutosave();
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
        for (Tool t : Tool.values()) keys.define("tool." + t.id, () -> L10n.t("action.tool", "tool", t.label()), () -> ed.setTool(t), KeyMap.key(toolKeys[t.ordinal()]));

        keys.define("brush.smaller", "action.brush.smaller", () -> ed.setBrushSize(ed.brushSize() - 1),
                KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        keys.define("brush.larger", "action.brush.larger", () -> ed.setBrushSize(ed.brushSize() + 1),
                KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET, java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        keys.define("color.prev", "action.color.prev", () -> ed.stepColor(-1), KeyMap.key(KeyEvent.VK_COMMA));
        keys.define("color.next", "action.color.next", () -> ed.stepColor(1), KeyMap.key(KeyEvent.VK_PERIOD));
        keys.define("node.prev", "action.node.prev", () -> ed.stepNode(-1), KeyMap.key(KeyEvent.VK_SEMICOLON));
        keys.define("node.next", "action.node.next", () -> ed.stepNode(1), KeyMap.key(KeyEvent.VK_QUOTE));
        keys.define("parent.prev", "action.parent.prev", () -> ed.stepGhost(-1), KeyMap.key(KeyEvent.VK_OPEN_BRACKET));
        keys.define("parent.next", "action.parent.next", () -> ed.stepGhost(1), KeyMap.key(KeyEvent.VK_CLOSE_BRACKET));

        keys.define("edit.undo", "action.edit.undo", () -> {
            canvas.deselect();
            ed.undo();
        }, KeyMap.ctrl(KeyEvent.VK_Z));
        keys.define("edit.redo", "action.edit.redo", ed::redo, KeyMap.ctrlShift(KeyEvent.VK_Z), KeyMap.ctrl(KeyEvent.VK_Y));
        keys.define("edit.delete", "action.edit.delete", () -> {
            if (canvas.hasSelection()) canvas.deleteSelection();
            else anchorMenus.delete();
        }, () -> canvas.hasSelection() || anchorMenus.hasAnchor(), KeyMap.key(KeyEvent.VK_DELETE), KeyMap.key(KeyEvent.VK_BACK_SPACE));
        keys.define("edit.deselect", "action.edit.deselect", canvas::deselect, canvas::hasSelection, KeyMap.key(KeyEvent.VK_ESCAPE));
        keys.define("edit.selectAll", "action.edit.selectAll", () -> {
            ed.setTool(Tool.SELECT);
            canvas.selectAll();
        }, KeyMap.ctrl(KeyEvent.VK_A));

        Dir[] dirs = {Dir.N, Dir.E, Dir.S, Dir.W};
        String[] dirNames = {"up", "right", "down", "left"};
        String[] dirLabels = {"action.anchor.up", "action.anchor.right", "action.anchor.down", "action.anchor.left"};
        int[] arrowKeys = {KeyEvent.VK_UP, KeyEvent.VK_RIGHT, KeyEvent.VK_DOWN, KeyEvent.VK_LEFT};
        for (int i = 0; i < 4; i++) {
            Dir d = dirs[i];
            keys.define("anchor." + dirNames[i], dirLabels[i], () -> anchorMenus.point(d),
                    () -> ed.tool() == Tool.ANCHOR && anchorMenus.hasAnchor(), KeyMap.key(arrowKeys[i]));
        }

        keys.define("file.new", "action.file.new", this::newProject, KeyMap.ctrl(KeyEvent.VK_N));
        keys.define("file.open", "action.file.open", this::open, KeyMap.ctrl(KeyEvent.VK_O));
        keys.define("file.save", "action.file.save", this::save, KeyMap.ctrl(KeyEvent.VK_S));
        keys.define("file.saveAs", "action.file.saveAs", this::saveAs, KeyMap.ctrlShift(KeyEvent.VK_S));
        keys.define("file.export", "action.file.export", this::export, KeyMap.ctrl(KeyEvent.VK_E));
        keys.define("file.backups", "action.file.backups", this::openBackups, none);
        keys.define("file.home", "action.file.home", this::goHome, none);
        keys.define("file.quit", "action.file.quit", () -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING)), KeyMap.ctrl(KeyEvent.VK_Q));

        keys.define("node.new", "action.node.new", () -> nodes.create(null), KeyMap.shift(KeyEvent.VK_A));
        keys.define("node.import", "action.node.import", nodes::importImages, none);
        keys.define("node.duplicate", "action.node.duplicate", nodes::duplicate, KeyMap.shift(KeyEvent.VK_D));
        keys.define("node.edit", "action.node.edit", () -> library.renameSelected(), KeyMap.key(KeyEvent.VK_F2));
        keys.define("node.resize", "action.node.resize", nodes::resize, KeyMap.ctrl(KeyEvent.VK_R));
        keys.define("node.root", "action.node.root", () -> nodes.setRoot(ed.node()), none);
        keys.define("node.delete", "action.node.delete", nodes::delete, none);

        keys.define("view.zoomIn", "action.view.zoomIn", () -> canvas.zoomStep(1), KeyMap.key(KeyEvent.VK_EQUALS));
        keys.define("view.zoomOut", "action.view.zoomOut", () -> canvas.zoomStep(-1), KeyMap.key(KeyEvent.VK_MINUS));
        keys.define("view.fit", "action.view.fit", canvas::fit, KeyMap.key(KeyEvent.VK_0));
        keys.define("view.grid", "action.view.grid", () -> ed.setGrid(!ed.grid()), none);
        keys.define("view.symmetry", "action.view.symmetry", () -> {
            Symmetry[] all = Symmetry.values();
            ed.setSymmetry(all[(ed.symmetry().ordinal() + 1) % all.length]);
        }, none);
        keys.define("view.rectFill", "action.view.rectFill", () -> ed.setRectFilled(!ed.rectFilled()), none);
        keys.define("render.reroll", "action.render.reroll", render::reroll, none);
        keys.define("render.fit", "action.render.fit", render::fit, none);
        keys.define("keys.edit", "action.keys.edit", this::editKeys, none);
    }

    private JMenuBar menus() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu(L10n.t("menu.file"));
        for (String id : List.of("file.new", "file.open", "file.save", "file.saveAs")) file.add(keys.menuItem(id));
        file.addSeparator();
        file.add(keys.menuItem("file.export"));
        file.addSeparator();
        file.add(keys.menuItem("file.home"));
        file.add(keys.menuItem("file.backups"));
        file.addSeparator();
        file.add(keys.menuItem("file.quit"));
        bar.add(file);

        JMenu edit = new JMenu(L10n.t("menu.edit"));
        for (String id : List.of("edit.undo", "edit.redo")) edit.add(keys.menuItem(id));
        edit.addSeparator();
        for (String id : List.of("edit.selectAll", "edit.deselect", "edit.delete")) edit.add(keys.menuItem(id));
        edit.addSeparator();
        edit.add(keys.menuItem("keys.edit"));
        bar.add(edit);

        JMenu node = new JMenu(L10n.t("menu.node"));
        for (String id : List.of("node.new", "node.import", "node.duplicate", "node.edit", "node.resize", "node.root")) {
            node.add(keys.menuItem(id));
        }
        node.addSeparator();
        for (String id : List.of("node.prev", "node.next", "parent.prev", "parent.next")) node.add(keys.menuItem(id));
        node.addSeparator();
        node.add(keys.menuItem("node.delete"));
        bar.add(node);

        JMenu tools = new JMenu(L10n.t("menu.tools"));
        for (Tool t : Tool.values()) tools.add(keys.menuItem("tool." + t.id));
        tools.addSeparator();
        for (String id : List.of("brush.smaller", "brush.larger", "color.prev", "color.next", "view.symmetry", "view.rectFill")) {
            tools.add(keys.menuItem(id));
        }
        bar.add(tools);

        JMenu view = new JMenu(L10n.t("menu.view"));
        for (String id : List.of("view.zoomIn", "view.zoomOut", "view.fit", "view.grid")) view.add(keys.menuItem(id));
        view.addSeparator();
        for (String id : List.of("render.fit", "render.reroll")) view.add(keys.menuItem(id));
        view.addSeparator();
        view.add(themeMenu());
        view.add(languageMenu());
        bar.add(view);
        return bar;
    }

    private JComponent canvasPanel() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        ButtonGroup bg = new ButtonGroup();
        for (Tool t : Tool.values()) {
            JToggleButton b = new JToggleButton(PixelIcon.of(t.icon)) {
                @Override
                public String getToolTipText(java.awt.event.MouseEvent e) {
                    return ToolHelp.title(t, MainWindow.this::keyHint);
                }
            };
            ToolTipManager.sharedInstance().registerComponent(b);
            b.getAccessibleContext().setAccessibleName(t.label());
            b.setFocusable(false);
            b.addActionListener(e -> ed.setTool(t));
            bg.add(b);
            toolButtons.put(t, b);
            bar.add(b);
        }
        bar.addSeparator();
        bar.add(new JLabel(" " + L10n.t("toolbar.size") + " "));
        brush.setToolTipText(L10n.t("toolbar.size.tip"));
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
        parentBar.add(new JLabel(" " + L10n.t("toolbar.symmetry") + " "));
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
        parentBar.add(new JLabel(" " + L10n.t("toolbar.parent") + " "));
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
        if (ed.node() != null && ed.node().root == null) parentLabel.setText(L10n.t("parent.noRootAnchor"));
        else if (cands.isEmpty()) parentLabel.setText(L10n.t("parent.none"));
        else if (g == null) parentLabel.setText(L10n.t("parent.hidden", "count", cands.size()));
        else parentLabel.setText(L10n.t("parent.showing", "label", g.label(), "index", cands.indexOf(g) + 1, "count", cands.size()));
        String name = ed.file() == null ? L10n.t("window.untitled") : ed.file().getFileName().toString();
        setTitle((ed.dirty() ? "*" : "") + L10n.t("window.title", "name", name));
    }

    // ---- files ----

    private boolean confirmDiscard() {
        ed.flush();
        if (!ed.dirty()) return true;
        int r = JOptionPane.showConfirmDialog(this, L10n.t("dialog.unsaved.text"), L10n.t("dialog.unsaved.title"), JOptionPane.YES_NO_CANCEL_OPTION);
        if (r == JOptionPane.YES_OPTION) return save();
        return r == JOptionPane.NO_OPTION;
    }

    private void newProject() {
        if (!confirmDiscard()) return;
        ed.load(Project.createDefault(), null);
    }

    private void open() {
        if (!confirmDiscard()) return;
        Path f = chooseFile(L10n.t("dialog.open.title"), FileDialog.LOAD, AppHome.compositionsDir(), null,
                ProjectIO.EXTENSION, ProjectIO.LEGACY_EXTENSION);
        if (f != null) openFile(f);
    }

    public void openFile(Path f) {
        try {
            Project p = ProjectIO.load(f);
            ed.load(p, f);
            lastDir = f.toAbsolutePath().getParent();
            recent.add(f, settings.recentProjects);
        } catch (IOException | RuntimeException ex) {
            JOptionPane.showMessageDialog(this, L10n.t("dialog.open.error", "file", f.getFileName(), "reason", ex.getMessage()),
                    L10n.t("dialog.open.errorTitle"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean save() {
        // A file from before the rename is saved as a new .zart file rather than overwritten.
        if (ed.file() == null || ed.file().getFileName().toString().endsWith(ProjectIO.LEGACY_EXTENSION)) return saveAs();
        return writeProject(ed.file());
    }

    private boolean saveAs() {
        String suggested = (ed.file() != null ? baseName(ed.file()) : L10n.t("file.defaultName")) + ProjectIO.EXTENSION;
        // New projects start in the compositions folder; saved ones start where they already are.
        Path start = ed.file() != null ? ed.file().toAbsolutePath().getParent() : AppHome.compositionsDir();
        Path f = chooseFile(L10n.t("dialog.save.title"), FileDialog.SAVE, start, suggested, ProjectIO.EXTENSION);
        return f != null && writeProject(f);
    }

    private boolean writeProject(Path f) {
        ed.flush();
        try {
            ProjectIO.save(ed.project(), f);
            ed.markSaved(f);
            lastDir = f.toAbsolutePath().getParent();
            recent.add(f, settings.recentProjects);
            status.setText(L10n.t("status.saved", "file", f));
            if (settings.backupKeep > 0) {
                try {
                    backups.backup(ed.project(), baseName(f), settings.backupKeep, java.time.LocalDateTime.now());
                } catch (IOException ex) {
                    status.setText(L10n.t("status.savedBackupFailed", "file", f, "reason", ex.getMessage()));
                }
            }
            return true;
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, L10n.t("dialog.save.error", "reason", ex.getMessage()), L10n.t("dialog.save.errorTitle"), JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    /**
     * Shows the platform's native file dialog, listing files with any of the given extensions.
     * When saving, a name without the first extension gets it added.
     */
    private Path chooseFile(String title, int mode, Path startDir, String suggested, String... extensions) {
        String extension = extensions[0];
        FileDialog fd = new FileDialog(this, title, mode);
        Path start = startDir != null ? startDir : lastDir != null ? lastDir : AppHome.compositionsDir();
        fd.setDirectory(start.toString());
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
                int r = JOptionPane.showConfirmDialog(this, L10n.t("dialog.replace", "file", f.getFileName()), title,
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

    // ---- backups and appearance ----

    private void restartAutosave() {
        autosave.stop();
        if (settings.autosaveMinutes <= 0) return;
        autosave.setDelay(settings.autosaveMinutes * 60_000);
        autosave.setInitialDelay(settings.autosaveMinutes * 60_000);
        autosave.start();
    }

    /** Writes unsaved work to the project's autosave file in the backups folder. */
    private void autosave() {
        if (!ed.dirty()) return;
        String base = ed.file() == null ? L10n.t("window.untitled") : baseName(ed.file());
        try {
            Path f = backups.autosave(ed.project(), base);
            status.setText(L10n.t("status.autosaved", "file", f));
        } catch (IOException | RuntimeException ex) {
            status.setText(L10n.t("status.autosaveFailed", "reason", ex.getMessage()));
        }
    }

    private void openBackups() {
        Path dir = backups.dir();
        try {
            java.nio.file.Files.createDirectories(dir);
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(dir.toFile());
                return;
            }
        } catch (IOException | RuntimeException ignored) {
            // fall through and show the path instead
        }
        JOptionPane.showMessageDialog(this, L10n.t("dialog.backups.text", "path", dir), L10n.t("dialog.backups.title"), JOptionPane.INFORMATION_MESSAGE);
    }

    /** View > Theme, rebuilt each time it opens so it lists the themes in themes.json. */
    private JMenu themeMenu() {
        JMenu menu = new JMenu(L10n.t("menu.theme"));
        menu.addMenuListener(new javax.swing.event.MenuListener() {
            @Override
            public void menuSelected(javax.swing.event.MenuEvent e) {
                menu.removeAll();
                Appearance look = Appearance.get();
                ButtonGroup group = new ButtonGroup();
                for (String name : look.themeNames()) {
                    JRadioButtonMenuItem item = new JRadioButtonMenuItem(name, name.equals(look.activeTheme()));
                    item.addActionListener(a -> {
                        String problem = look.select(name);
                        status.setText(problem != null ? problem : L10n.t("status.theme", "name", name));
                        repaint();
                    });
                    group.add(item);
                    menu.add(item);
                }
                menu.addSeparator();
                JMenuItem where = new JMenuItem(L10n.t("menu.theme.edit"));
                where.addActionListener(a -> openSettingsFolder());
                menu.add(where);
            }

            @Override
            public void menuDeselected(javax.swing.event.MenuEvent e) {
            }

            @Override
            public void menuCanceled(javax.swing.event.MenuEvent e) {
            }
        });
        return menu;
    }

    private void openSettingsFolder() {
        Path dir = AppHome.settingsDir();
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(dir.toFile());
                return;
            }
        } catch (IOException | RuntimeException ignored) {
            // fall through and show the path instead
        }
        JOptionPane.showMessageDialog(this, L10n.t("dialog.settings.text", "path", dir), L10n.t("dialog.settings.title"), JOptionPane.INFORMATION_MESSAGE);
    }

    /** Closes this project, asking about unsaved changes, and returns to the home screen. */
    private void goHome() {
        if (!confirmDiscard()) return;
        autosave.stop();
        dispose();
        new HomeWindow().setVisible(true);
    }

    /** View > Language, rebuilt each time it opens so it lists the files in settings/languages. */
    private JMenu languageMenu() {
        JMenu menu = new JMenu(L10n.t("menu.language"));
        menu.addMenuListener(new javax.swing.event.MenuListener() {
            @Override
            public void menuSelected(javax.swing.event.MenuEvent e) {
                menu.removeAll();
                ButtonGroup group = new ButtonGroup();
                for (L10n.Option o : L10n.available(Startup.languagesDir())) {
                    JRadioButtonMenuItem item = new JRadioButtonMenuItem(o.name(), o.code().equals(L10n.code()));
                    item.addActionListener(a -> switchLanguage(o.code()));
                    group.add(item);
                    menu.add(item);
                }
                menu.addSeparator();
                JMenuItem where = new JMenuItem(L10n.t("menu.language.folder"));
                where.addActionListener(a -> openFolder(Startup.languagesDir()));
                menu.add(where);
            }

            @Override
            public void menuDeselected(javax.swing.event.MenuEvent e) {
            }

            @Override
            public void menuCanceled(javax.swing.event.MenuEvent e) {
            }
        });
        return menu;
    }

    /**
     * Saves the choice, then reopens this window in the new language with the same project.
     * Unsaved changes carry over; undo history does not, so ask first when there is some.
     */
    private void switchLanguage(String code) {
        if (code.equals(L10n.code())) return;
        if (ed.canUndo()) {
            int r = JOptionPane.showConfirmDialog(this, L10n.t("language.reopen"), L10n.t("menu.language"),
                    JOptionPane.OK_CANCEL_OPTION);
            if (r != JOptionPane.OK_OPTION) return;
        }
        ed.flush();
        settings.language = code;
        try {
            settings.save(keys);
        } catch (IOException ex) {
            status.setText(L10n.t("settings.error.write", "file", Settings.FILE_NAME, "reason", ex.getMessage()));
        }
        String problems = L10n.use(Startup.languagesDir(), code);
        boolean dirty = ed.dirty();
        autosave.stop();
        dispose();
        MainWindow next = new MainWindow(ed.project(), ed.file());
        if (dirty) next.ed.markDirty();
        next.setVisible(true);
        if (problems != null) next.showStatus(problems);
    }

    private void openFolder(Path dir) {
        try {
            java.nio.file.Files.createDirectories(dir);
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(dir.toFile());
                return;
            }
        } catch (IOException | RuntimeException ignored) {
            // fall through and show the path instead
        }
        JOptionPane.showMessageDialog(this, L10n.t("dialog.settings.text", "path", dir), L10n.t("dialog.settings.title"),
                JOptionPane.INFORMATION_MESSAGE);
    }

    /** Shows a message in the status bar. */
    public void showStatus(String text) {
        status.setText(text);
    }

    // ---- export ----

    /**
     * Resizes the open dialog holding {@code c} to fit contents that changed after it opened.
     * With {@code shrink} false it only grows, so small changes do not make it jump.
     */
    private static void fitDialog(Component c, boolean shrink) {
        Window win = SwingUtilities.getWindowAncestor(c);
        if (win == null || !win.isShowing()) return;
        if (shrink) {
            win.pack();
            return;
        }
        Dimension pref = win.getPreferredSize(), size = win.getSize();
        if (pref.width > size.width || pref.height > size.height) {
            win.setSize(Math.max(pref.width, size.width), Math.max(pref.height, size.height));
            win.validate();
        }
    }

    private void export() {
        ed.flush();
        Composition c = ed.composition();
        if (c.isEmpty()) {
            JOptionPane.showMessageDialog(this, L10n.t("export.error.empty"), L10n.t("export.dialog.title"),
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JComboBox<Exporter.Format> format = new JComboBox<>(Exporter.Format.values());
        format.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                super.getListCellRendererComponent(l, v, i, s, f);
                Exporter.Format fm = (Exporter.Format) v;
                setText(L10n.t("export.format.item", "name", fm.label(), "extension", fm.extension));
                return this;
            }
        });
        format.setSelectedItem(exportFormat);
        JSpinner padding = new JSpinner(new SpinnerNumberModel(exportPadding, 0, 256, 1));
        JSpinner scale = new JSpinner(new SpinnerNumberModel(exportScale, 1, 64, 1));
        JSpinner pixelSize = new JSpinner(new SpinnerNumberModel(exportPixelSize, 0.0001, 100.0, 0.001));
        pixelSize.setEditor(new JSpinner.NumberEditor(pixelSize, "0.0####"));
        pixelSize.setToolTipText(L10n.t("export.pixelSize.tip"));
        JLabel summary = new JLabel();
        summary.setForeground(Draw.MUTED);
        long parts = c.instances.size();
        long textures = c.instances.stream().map(i -> i.node).distinct().count();
        Runnable update = () -> {
            Exporter.Format fm = (Exporter.Format) format.getSelectedItem();
            scale.setEnabled(fm.scalable());
            padding.setEnabled(fm.padded());
            pixelSize.setEnabled(fm == Exporter.Format.GODOT_3D);
            if (fm.godot()) {
                summary.setText("<html>" + L10n.t("export.summary.godot", "parts", L10n.plural("export.parts", parts),
                        "textures", L10n.plural("export.textures", textures), "root", c.root.node.ref())
                        + "<br>" + L10n.t("export.summary.godot.note") + "</html>");
                return;
            }
            int pad = (Integer) padding.getValue(), sc = fm.scalable() ? (Integer) scale.getValue() : 1;
            int w = (c.width + 2 * pad) * sc, h = (c.height + 2 * pad) * sc;
            String layers = fm == Exporter.Format.FLAT_PNG ? L10n.t("export.oneImage") : L10n.plural("export.layers", c.layers.size());
            summary.setText(L10n.t("export.summary.image", "width", w, "height", h, "layers", layers,
                    "colors", L10n.plural("export.colors", ed.project().palette.size())));
        };
        format.addActionListener(e -> {
            update.run();
            fitDialog(summary, true);
        });
        padding.addChangeListener(e -> {
            update.run();
            fitDialog(summary, false);
        });
        scale.addChangeListener(e -> {
            update.run();
            fitDialog(summary, false);
        });
        update.run();
        JPanel form = NodeActions.form(new String[]{L10n.t("export.label.format"), L10n.t("export.label.padding"),
                        L10n.t("export.label.scale"), L10n.t("export.label.pixelSize"), ""},
                format, padding, scale, pixelSize, summary);
        if (!c.warnings.isEmpty()) {
            JLabel warn = new JLabel("<html>" + String.join("<br>", c.warnings) + "</html>");
            warn.setForeground(Draw.WARNING);
            form.add(warn, NodeActions.gbc(5, 0, 2));
        }
        int r = JOptionPane.showConfirmDialog(this, form, L10n.t("export.dialog.title"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;
        exportFormat = (Exporter.Format) format.getSelectedItem();
        exportPadding = (Integer) padding.getValue();
        exportScale = (Integer) scale.getValue();
        exportPixelSize = ((Number) pixelSize.getValue()).doubleValue();

        String base = ed.file() == null ? L10n.t("file.defaultName") : baseName(ed.file());
        Path f = chooseFile(L10n.t("export.file.title", "format", exportFormat.label()), FileDialog.SAVE, null, base + exportFormat.extension, exportFormat.extension);
        if (f == null) return;
        try {
            byte[] data = exportFormat.godot()
                    ? Exporter.godot(exportFormat, c, ed.project().palette, exportPixelSize)
                    : Exporter.export(exportFormat, c, ed.project().palette, exportPadding, exportFormat.scalable() ? exportScale : 1);
            Files.write(f, data);
            status.setText(L10n.t("status.exported", "file", f));
        } catch (IOException | RuntimeException ex) {
            JOptionPane.showMessageDialog(this, L10n.t("export.error.failed", "reason", ex.getMessage()), L10n.t("export.dialog.title"), JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---- keys ----

    /** The first key bound to an action, readable, or "" when it has none. */
    String keyHint(String id) {
        if (keys.def(id) == null) return "";
        List<KeyStroke> b = keys.bindings(id);
        return b.isEmpty() ? "" : KeyMap.describe(b.get(0));
    }

    private void editKeys() {
        Map<String, List<KeyStroke>> next = new KeyBindingsDialog(this, keys).showDialog();
        if (next == null) return;
        keys.setBindings(next);
        anchorPanel.refreshHelp();
        try {
            status.setText(L10n.t("status.keysSaved", "file", settings.save(keys)));
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, L10n.t("dialog.keys.error", "reason", ex.getMessage()), L10n.t("dialog.keys.title"),
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}
