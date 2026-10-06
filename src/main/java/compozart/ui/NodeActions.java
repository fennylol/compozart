package compozart.ui;

import compozart.model.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Node library commands: create, duplicate, edit, resize, delete, reorder, set as root. */
final class NodeActions {
    private final Editor ed;
    private final Component parent;
    /** The folder new nodes go into: the one selected in the library, or the current node's folder. */
    private java.util.function.Supplier<String> currentFolder = () -> "";

    NodeActions(Editor ed, Component parent) {
        this.ed = ed;
        this.parent = parent;
    }

    void setFolderSource(java.util.function.Supplier<String> s) {
        currentFolder = s;
    }

    private Project p() {
        return ed.project();
    }

    private String validate(String name, String variant, Node except) {
        if (name.isEmpty()) return "The name cannot be empty.";
        Node other = p().find(name, variant);
        if (other != null && other != except) return "A node " + new NodeRef(name, variant) + " already exists.";
        return null;
    }

    /** Creates a node, optionally with a preset name (from an anchor's target). */
    void create(String presetName) {
        String name = presetName != null ? presetName : p().uniqueName("part", "");
        String variant = presetName != null && !p().named(presetName).isEmpty() ? p().uniqueVariant(presetName, "variant") : "";
        JTextField nameF = new JTextField(name, 16);
        JTextField varF = new JTextField(variant, 16);
        JSpinner size = new JSpinner(new SpinnerNumberModel(Node.DEFAULT_SIZE, 1, Node.MAX_SIZE, 1));
        JCheckBox root = new JCheckBox("Add a root anchor (center, pointing down)", true);
        JPanel form = form(new String[]{"Name", "Variant", "Size"}, nameF, varF, size);
        form.add(root, gbc(3, 0, 2));
        while (true) {
            if (!ask("New node", form, nameF)) return;
            String n = nameF.getText().trim(), v = varF.getText().trim();
            String err = validate(n, v, null);
            if (err != null) {
                error(err);
                continue;
            }
            int s = (Integer) size.getValue();
            Node node = new Node(n, v, s);
            if (root.isSelected()) node.root = RootAnchor.centered(s);
            node.folder = currentFolder.get();
            int at = Math.min(p().nodes.size(), ed.nodeIndex() + 1);
            ed.edit(null, () -> {
                p().nodes.add(at, node);
                if (p().root == null || p().find(p().root) == null) p().root = node.ref();
            });
            ed.selectNode(node);
            return;
        }
    }

    /** Copies the current node as a new variant of the same name. */
    void duplicate() {
        Node n = ed.node();
        if (n == null) return;
        Node c = n.copy();
        c.variant = p().uniqueVariant(n.name, n.variant.isEmpty() ? "copy" : n.variant + " copy");
        int at = ed.nodeIndex() + 1;
        ed.edit(null, () -> p().nodes.add(at, c));
        ed.selectNode(c);
    }

    void edit() {
        Node n = ed.node();
        if (n == null) return;
        JTextField nameF = new JTextField(n.name, 16);
        JTextField varF = new JTextField(n.variant, 16);
        JPanel form = form(new String[]{"Name", "Variant"}, nameF, varF);
        while (true) {
            if (!ask("Rename node", form, nameF)) return;
            String name = nameF.getText().trim(), variant = varF.getText().trim();
            String err = validate(name, variant, n);
            if (err != null) {
                error(err);
                continue;
            }
            String oldName = n.name;
            NodeRef oldRef = n.ref();
            boolean renamed = !name.equals(oldName);
            boolean retarget = false;
            if (renamed) {
                boolean othersKeepName = p().named(oldName).size() > 1;
                boolean targeted = p().nodes.stream().anyMatch(x -> x.anchors.stream().anyMatch(a -> a.target.equals(oldName)));
                if (targeted && !othersKeepName) {
                    int r = JOptionPane.showConfirmDialog(parent,
                            "Anchors target “" + oldName + "”. Point them at “" + name + "” too?",
                            "Rename", JOptionPane.YES_NO_CANCEL_OPTION);
                    if (r == JOptionPane.CANCEL_OPTION || r == JOptionPane.CLOSED_OPTION) return;
                    retarget = r == JOptionPane.YES_OPTION;
                }
            }
            boolean doRetarget = retarget;
            ed.edit(null, () -> {
                if (doRetarget) p().renameTarget(oldName, name);
                if (oldRef.equals(p().root)) p().root = new NodeRef(name, variant);
                // Anchors that still point at this node keep pointing at it when its variant name changes.
                boolean stillTargeted = !renamed || doRetarget;
                if (stillTargeted) {
                    for (Node x : p().nodes) {
                        for (NamedAnchor a : x.anchors) {
                            if (!a.target.equals(name)) continue;
                            if (oldRef.variant().equals(a.variant)) a.variant = variant;
                            if (oldRef.variant().equals(a.endVariant)) a.endVariant = variant;
                        }
                    }
                }
                n.name = name;
                n.variant = variant;
            });
            return;
        }
    }

    void resize() {
        Node n = ed.node();
        if (n == null) return;
        ed.flush();
        JSpinner size = new JSpinner(new SpinnerNumberModel(n.size(), 1, Node.MAX_SIZE, 1));
        JToggleButton[] fix = new JToggleButton[9];
        ButtonGroup bg = new ButtonGroup();
        JPanel grid = new JPanel(new GridLayout(3, 3, 2, 2));
        String[] arrows = {"↖", "↑", "↗", "←", "•", "→", "↙", "↓", "↘"};
        for (int i = 0; i < 9; i++) {
            fix[i] = new JToggleButton(arrows[i]);
            fix[i].setMargin(new Insets(2, 6, 2, 6));
            bg.add(fix[i]);
            grid.add(fix[i]);
        }
        fix[4].setSelected(true);
        JPanel gridWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        gridWrap.add(grid);
        JPanel form = form(new String[]{"Size", "Keep fixed"}, size, gridWrap);
        if (!ask("Resize " + n.ref(), form, size)) return;
        int s = (Integer) size.getValue();
        int k = 0;
        for (int i = 0; i < 9; i++) if (fix[i].isSelected()) k = i;
        int fx = k % 3, fy = k / 3;
        if (s == n.size()) return;
        int lost = n.anchorsLostByResize(s, fx, fy);
        if (lost > 0) {
            int r = JOptionPane.showConfirmDialog(parent, lost + (lost == 1 ? " anchor falls" : " anchors fall")
                    + " outside the new size and will be removed. Resize anyway?", "Resize", JOptionPane.OK_CANCEL_OPTION);
            if (r != JOptionPane.OK_OPTION) return;
        }
        ed.edit(null, () -> n.resize(s, fx, fy));
    }

    void delete() {
        Node n = ed.node();
        if (n == null) return;
        int r = JOptionPane.showConfirmDialog(parent, "Delete " + n.ref() + "?", "Delete node", JOptionPane.OK_CANCEL_OPTION);
        if (r != JOptionPane.OK_OPTION) return;
        int i = ed.nodeIndex();
        ed.edit(null, () -> {
            p().nodes.remove(i);
            if (n.ref().equals(p().root)) p().root = null;
        });
    }

    /** Moves the current node up or down among the nodes in its own folder. */
    void move(int delta) {
        Node n = ed.node();
        if (n == null) return;
        List<Node> nodes = p().nodes;
        List<Node> siblings = p().nodesIn(n.folder);
        int k = siblings.indexOf(n) + delta;
        if (k < 0 || k >= siblings.size()) return;
        Node other = siblings.get(k);
        int i = nodes.indexOf(n), j = nodes.indexOf(other);
        ed.edit(null, () -> {
            nodes.set(i, other);
            nodes.set(j, n);
        });
        ed.selectNode(n);
    }

    // ---- importing images ----

    private java.nio.file.Path importDir;

    /** Asks for one or more images and adds each as a node in the current folder. */
    void importImages() {
        Window w = SwingUtilities.getWindowAncestor(parent);
        FileDialog fd = new FileDialog(w instanceof Frame f ? f : null, "Import image as node", FileDialog.LOAD);
        fd.setMultipleMode(true);
        if (importDir != null) fd.setDirectory(importDir.toString());
        Set<String> suffixes = new HashSet<>();
        for (String s : javax.imageio.ImageIO.getReaderFileSuffixes()) suffixes.add(s.toLowerCase(java.util.Locale.ROOT));
        fd.setFilenameFilter((dir, name) -> {
            int dot = name.lastIndexOf('.');
            return dot > 0 && suffixes.contains(name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT))
                    || new java.io.File(dir, name).isDirectory();
        });
        fd.setVisible(true);
        java.io.File[] files = fd.getFiles();
        if (files == null || files.length == 0) return;
        importDir = files[0].toPath().toAbsolutePath().getParent();

        List<java.io.File> ok = new ArrayList<>();
        List<java.awt.image.BufferedImage> images = new ArrayList<>();
        StringBuilder problems = new StringBuilder();
        for (java.io.File f : files) {
            try {
                java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(f);
                if (img == null) problems.append(f.getName()).append(": not a readable image\n");
                else if (Math.max(img.getWidth(), img.getHeight()) > Node.MAX_SIZE) {
                    problems.append(f.getName()).append(": ").append(img.getWidth()).append("\u00d7").append(img.getHeight())
                            .append(" is larger than ").append(Node.MAX_SIZE).append("\u00d7").append(Node.MAX_SIZE).append("\n");
                } else {
                    ok.add(f);
                    images.add(img);
                }
            } catch (java.io.IOException e) {
                problems.append(f.getName()).append(": ").append(e.getMessage()).append("\n");
            }
        }
        if (!problems.isEmpty()) {
            JOptionPane.showMessageDialog(parent, (ok.isEmpty() ? "" : "These files will be skipped:\n") + problems,
                    "Import image", JOptionPane.WARNING_MESSAGE);
        }
        if (ok.isEmpty()) return;
        importDialog(ok, images);
    }

    private void importDialog(List<java.io.File> files, List<java.awt.image.BufferedImage> images) {
        boolean single = files.size() == 1;
        String base = baseName(files.get(0).getName());
        JTextField nameF = new JTextField(base, 16);
        JTextField varF = new JTextField(p().find(base, "") == null ? "" : p().uniqueVariant(base, "import"), 16);

        StringBuilder stats = new StringBuilder("<html>");
        int fresh = 0;
        for (int i = 0; i < files.size() && i < 12; i++) {
            compozart.io.ImageImport.Analysis a = compozart.io.ImageImport.analyze(images.get(i), p().palette);
            fresh += a.fresh();
            stats.append(escape(files.get(i).getName())).append(" \u00b7 ").append(a.width()).append("\u00d7").append(a.height())
                    .append(" \u00b7 ").append(a.colors()).append(a.colors() == 1 ? " color" : " colors")
                    .append(", ").append(a.inPalette()).append(" already in the palette<br>");
        }
        if (files.size() > 12) stats.append("and ").append(files.size() - 12).append(" more<br>");
        int room = Palette.MAX - p().palette.size();
        stats.append("The palette has room for ").append(room).append(room == 1 ? " more color." : " more colors.");
        if (fresh > room) stats.append("<br>Colors that do not fit use the nearest palette color.");
        stats.append("</html>");

        JRadioButton add = new JRadioButton("Add new colors to the palette", true);
        add.setToolTipText("Keeps the image's colors exactly while there is room in the palette");
        JRadioButton nearest = new JRadioButton("Match the existing palette");
        nearest.setToolTipText("Leaves the palette alone; every pixel uses the closest palette color");
        ButtonGroup bg = new ButtonGroup();
        bg.add(add);
        bg.add(nearest);
        JCheckBox root = new JCheckBox("Add a root anchor (center, pointing down)", true);

        JPanel form = new JPanel(new GridBagLayout());
        int row = 0;
        if (single) {
            form.add(new JLabel("Name"), gbc(row, 0, 1));
            form.add(nameF, gbc(row++, 1, 1));
            form.add(new JLabel("Variant"), gbc(row, 0, 1));
            form.add(varF, gbc(row++, 1, 1));
        } else {
            form.add(new JLabel(files.size() + " images. Each becomes a node named after its file."), gbc(row++, 0, 2));
        }
        JLabel statsLabel = new JLabel(stats.toString());
        statsLabel.setForeground(Draw.MUTED);
        form.add(statsLabel, gbc(row++, 0, 2));
        form.add(add, gbc(row++, 0, 2));
        form.add(nearest, gbc(row++, 0, 2));
        form.add(root, gbc(row, 0, 2));

        while (true) {
            if (!ask("Import image", form, single ? nameF : add)) return;
            if (single) {
                String err = validate(nameF.getText().trim(), varF.getText().trim(), null);
                if (err != null) {
                    error(err);
                    continue;
                }
            }
            break;
        }
        compozart.io.ImageImport.Mode mode = add.isSelected() ? compozart.io.ImageImport.Mode.ADD_COLORS
                : compozart.io.ImageImport.Mode.NEAREST;
        String folder = currentFolder.get();
        int at = Math.min(p().nodes.size(), ed.nodeIndex() + 1);
        List<Node> created = new ArrayList<>();
        int[] approximated = {0};
        ed.edit(null, () -> {
            int k = at;
            for (int i = 0; i < files.size(); i++) {
                String name = single ? nameF.getText().trim() : baseName(files.get(i).getName());
                String variant = single ? varF.getText().trim() : p().find(name, "") == null ? "" : p().uniqueVariant(name, "import");
                compozart.io.ImageImport.Result r = compozart.io.ImageImport.apply(p(), images.get(i), mode, name, variant);
                Node n = r.node();
                n.folder = folder;
                if (root.isSelected()) n.root = RootAnchor.centered(n.size());
                p().nodes.add(k++, n);
                created.add(n);
                approximated[0] += r.approximated();
            }
            if (p().root == null || p().find(p().root) == null) p().root = created.get(0).ref();
        });
        ed.selectNode(created.get(created.size() - 1));
        if (approximated[0] > 0) {
            JOptionPane.showMessageDialog(parent, approximated[0] + (approximated[0] == 1 ? " pixel uses" : " pixels use")
                    + " the nearest palette color, because its exact color "
                    + (mode == compozart.io.ImageImport.Mode.NEAREST ? "is not in the palette." : "did not fit."),
                    "Import image", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /** A file name without its extension, usable as a node name. */
    private static String baseName(String file) {
        int dot = file.lastIndexOf('.');
        String b = (dot > 0 ? file.substring(0, dot) : file).trim();
        return b.isEmpty() ? "image" : b;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;");
    }

    // ---- folders ----

    /** Asks for a folder name and creates it inside {@code parent}. Returns the new path, or null. */
    String newFolder(String parent) {
        while (true) {
            String name = JOptionPane.showInputDialog(this.parent,
                    parent.isEmpty() ? "New folder name:" : "New folder in " + parent + ":", "New folder",
                    JOptionPane.PLAIN_MESSAGE);
            if (name == null) return null;
            String path;
            try {
                path = Project.join(parent, Project.normalizeFolder(name));
            } catch (IllegalArgumentException e) {
                error("Folder names cannot be empty.");
                continue;
            }
            if (path.isEmpty()) {
                error("Folder names cannot be empty.");
                continue;
            }
            if (p().hasFolder(path)) {
                error("The folder " + path + " already exists.");
                continue;
            }
            ed.edit(null, () -> p().addFolder(path));
            return path;
        }
    }

    /** Renames a folder in place. Returns the new path, or null. */
    String renameFolder(String path) {
        while (true) {
            String name = (String) JOptionPane.showInputDialog(this.parent, "Rename folder " + path + ":", "Rename folder",
                    JOptionPane.PLAIN_MESSAGE, null, null, Project.nameOf(path));
            if (name == null) return null;
            String trimmed = name.trim();
            if (trimmed.isEmpty() || trimmed.contains("/")) {
                error("A folder name cannot be empty or contain \"/\".");
                continue;
            }
            String target = Project.join(Project.parentOf(path), trimmed);
            if (target.equals(path)) return path;
            if (p().hasFolder(target)) {
                error("The folder " + target + " already exists.");
                continue;
            }
            ed.edit(null, () -> p().moveFolder(path, target));
            return target;
        }
    }

    /** Deletes a folder after asking. Its contents move up one level. */
    void deleteFolder(String path) {
        long count = p().nodes.stream().filter(n -> Project.isInside(n.folder, path)).count();
        String where = Project.parentOf(path).isEmpty() ? "the top level" : Project.parentOf(path);
        String msg = count == 0 && p().subfolders(path).isEmpty() ? "Delete the empty folder " + path + "?"
                : "Delete the folder " + path + "? Its contents (" + count + (count == 1 ? " node" : " nodes")
                + ") move up to " + where + ".";
        int r = JOptionPane.showConfirmDialog(parent, msg, "Delete folder", JOptionPane.OK_CANCEL_OPTION);
        if (r == JOptionPane.OK_OPTION) ed.edit(null, () -> p().deleteFolder(path));
    }

    void moveNodeTo(Node n, String folder) {
        if (n == null || n.folder.equals(folder)) return;
        ed.edit(null, () -> {
            p().addFolder(folder);
            n.folder = folder;
        });
    }

    /** Moves a folder into another one, merging if a folder of the same name is already there. */
    boolean moveFolderInto(String path, String newParent) {
        if (Project.isInside(newParent, path)) return false;
        String target = Project.join(newParent, Project.nameOf(path));
        if (target.equals(path)) return false;
        ed.edit(null, () -> p().moveFolder(path, target));
        return true;
    }

    void setRoot(Node n) {
        if (n == null || n.ref().equals(p().root)) return;
        ed.edit(null, () -> p().root = n.ref());
    }

    // ---- dialog helpers ----

    private boolean ask(String title, JComponent form, JComponent focus) {
        JOptionPane pane = new JOptionPane(form, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        JDialog d = pane.createDialog(parent, title);
        d.addWindowFocusListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowGainedFocus(java.awt.event.WindowEvent e) {
                focus.requestFocusInWindow();
                if (focus instanceof JTextField tf) tf.selectAll();
            }
        });
        d.setVisible(true);
        d.dispose();
        return Integer.valueOf(JOptionPane.OK_OPTION).equals(pane.getValue());
    }

    private void error(String msg) {
        JOptionPane.showMessageDialog(parent, msg, "Node", JOptionPane.WARNING_MESSAGE);
    }

    static JPanel form(String[] labels, JComponent... fields) {
        JPanel p = new JPanel(new GridBagLayout());
        for (int i = 0; i < labels.length; i++) {
            p.add(new JLabel(labels[i]), gbc(i, 0, 1));
            GridBagConstraints g = gbc(i, 1, 1);
            g.fill = GridBagConstraints.HORIZONTAL;
            g.weightx = 1;
            p.add(fields[i], g);
        }
        return p;
    }

    static GridBagConstraints gbc(int row, int col, int width) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridy = row;
        g.gridx = col;
        g.gridwidth = width;
        g.anchor = GridBagConstraints.WEST;
        g.insets = new Insets(3, 3, 3, 6);
        return g;
    }
}
