package compozart.ui;

import compozart.model.Node;
import compozart.model.Project;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/** Every node in the project, attached or not, filed in folders. Drag nodes and folders to refile them. */
final class NodeLibrary extends JPanel {
    private static final int THUMB = 28;
    // FlatLaf hides tree icons by default; folders need them to read as folders.
    private static final Icon OPEN_ICON = new com.formdev.flatlaf.icons.FlatTreeOpenIcon();
    private static final Icon CLOSED_ICON = new com.formdev.flatlaf.icons.FlatTreeClosedIcon();

    /** A library folder. "" is the top level, which is the tree's hidden root. */
    record FolderRow(String path) {
    }

    record NodeRow(Node node) {
    }

    private final Editor ed;
    private final NodeActions actions;
    private final JTree tree = new JTree(new DefaultTreeModel(new DefaultMutableTreeNode(new FolderRow(""))));
    private final Set<String> collapsed = new HashSet<>();
    private String lastKey;
    private Project lastProject;
    private Node lastSynced;
    private boolean syncing;

    private final java.util.function.Function<String, String> keyHint;

    NodeLibrary(Editor ed, NodeActions actions, java.util.function.Function<String, String> keyHint) {
        super(new BorderLayout());
        this.ed = ed;
        this.actions = actions;
        this.keyHint = keyHint;
        actions.setFolderSource(this::currentFolder);
        JLabel title = new JLabel("Node library");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.setBorder(BorderFactory.createEmptyBorder(6, 8, 4, 8));

        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        JButton newButton = button("new", "New node, folder or imported image, in the selected folder", null, () -> { });
        newButton.addActionListener(e -> newMenu().show(newButton, 0, newButton.getHeight()));
        bar.add(newButton);
        bar.add(button("rename", "Rename the selected node or folder", "node.edit", this::renameSelected));
        bar.add(button("duplicate", "Duplicate the node as a new variant", "node.duplicate", actions::duplicate));
        bar.add(button("resize", "Resize the node", "node.resize", actions::resize));
        bar.add(button("root", "Make the node the creature root", "node.root", () -> actions.setRoot(ed.node())));
        bar.add(button("up", "Move up within its folder", null, () -> actions.move(-1)));
        bar.add(button("down", "Move down within its folder", null, () -> actions.move(1)));
        bar.add(button("delete", "Delete the node or folder", "node.delete", this::deleteSelected));

        JPanel north = new JPanel(new BorderLayout());
        north.add(title, BorderLayout.NORTH);
        north.add(bar, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(0);
        tree.setToggleClickCount(0);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new Renderer());
        tree.setDragEnabled(true);
        tree.setDropMode(DropMode.ON);
        tree.setTransferHandler(new Dnd());
        add(new JScrollPane(tree), BorderLayout.CENTER);

        tree.addTreeSelectionListener(e -> {
            if (syncing) return;
            if (rowOf(tree.getSelectionPath()) instanceof NodeRow r) {
                lastSynced = r.node();
                ed.selectNode(r.node());
            }
        });
        tree.addTreeExpansionListener(new TreeExpansionListener() {
            @Override
            public void treeExpanded(TreeExpansionEvent e) {
                if (!syncing && rowOf(e.getPath()) instanceof FolderRow f) collapsed.remove(f.path());
            }

            @Override
            public void treeCollapsed(TreeExpansionEvent e) {
                if (!syncing && rowOf(e.getPath()) instanceof FolderRow f) collapsed.add(f.path());
            }
        });
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2 || !SwingUtilities.isLeftMouseButton(e)) return;
                Object row = rowOf(tree.getPathForLocation(e.getX(), e.getY()));
                if (row instanceof NodeRow) actions.edit();
                else if (row instanceof FolderRow f) toggle(f.path());
            }

            @Override
            public void mousePressed(MouseEvent e) {
                popup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                popup(e);
            }
        });
        ed.addListener(what -> refresh());
        refresh();
    }

    /** An icon button whose tooltip names the action and, when it has one, its current key. */
    private JButton button(String icon, String tip, String actionId, Runnable r) {
        JButton b = new JButton(PixelIcon.of(icon)) {
            @Override
            public String getToolTipText(MouseEvent e) {
                String k = actionId == null ? "" : keyHint.apply(actionId);
                return k.isEmpty() ? tip : tip + " (" + k + ")";
            }
        };
        ToolTipManager.sharedInstance().registerComponent(b);
        b.getAccessibleContext().setAccessibleName(tip);
        b.setMargin(new Insets(2, 3, 2, 3));
        b.addActionListener(e -> r.run());
        return b;
    }

    private static Object rowOf(TreePath p) {
        return p == null ? null : ((DefaultMutableTreeNode) p.getLastPathComponent()).getUserObject();
    }

    /** The folder new nodes and folders go into: the selected folder, or the selected node's folder. */
    String currentFolder() {
        Object row = rowOf(tree.getSelectionPath());
        if (row instanceof FolderRow f) return f.path();
        if (row instanceof NodeRow r) return r.node().folder;
        Node n = ed.node();
        return n == null ? "" : n.folder;
    }

    private String selectedFolder() {
        return rowOf(tree.getSelectionPath()) instanceof FolderRow f ? f.path() : null;
    }

    // ---- commands ----

    private void newFolder() {
        newFolderIn(currentFolder());
    }

    private void newFolderIn(String parent) {
        String path = actions.newFolder(parent);
        if (path != null) selectFolder(path);
    }

    /** The New button's menu: everything that can be created goes in the selected folder. */
    private JPopupMenu newMenu() {
        String here = currentFolder();
        JPopupMenu m = new JPopupMenu();
        m.add(item("Node" + in(here) + "\u2026", () -> actions.create(null)));
        m.add(item("Folder" + in(here) + "\u2026", () -> newFolderIn(here)));
        m.add(item("Import image" + in(here) + "\u2026", actions::importImages));
        return m;
    }

    /** Renames the selected folder, or the node being edited when no folder is selected. */
    void renameSelected() {
        editSelected();
    }

    private void editSelected() {
        String folder = selectedFolder();
        if (folder == null) {
            actions.edit();
            return;
        }
        String renamed = actions.renameFolder(folder);
        if (renamed != null) {
            remapCollapsed(folder, renamed);
            selectFolder(renamed);
        }
    }

    private void deleteSelected() {
        String folder = selectedFolder();
        if (folder != null) actions.deleteFolder(folder);
        else actions.delete();
    }

    private void toggle(String folder) {
        TreePath p = pathOfFolder(folder);
        if (p == null) return;
        if (tree.isExpanded(p)) tree.collapsePath(p);
        else tree.expandPath(p);
    }

    private void remapCollapsed(String from, String to) {
        Set<String> next = new HashSet<>();
        for (String c : collapsed) next.add(Project.isInside(c, from) ? to + c.substring(from.length()) : c);
        collapsed.clear();
        collapsed.addAll(next);
    }

    private void popup(MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        TreePath path = tree.getPathForLocation(e.getX(), e.getY());
        if (path != null) tree.setSelectionPath(path);
        else tree.clearSelection();
        Object row = rowOf(path);
        String here = row instanceof FolderRow f ? f.path() : row instanceof NodeRow r ? r.node().folder : "";
        JPopupMenu m = new JPopupMenu();
        m.add(item("New node" + in(here) + "…", () -> actions.create(null)));
        m.add(item("New folder" + in(here) + "…", () -> newFolderIn(here)));
        m.add(item("Import image" + in(here) + "\u2026", actions::importImages));
        if (row instanceof NodeRow r) {
            m.addSeparator();
            m.add(item("Duplicate as new variant", actions::duplicate));
            m.add(item("Rename\u2026", actions::edit));
            m.add(item("Resize…", actions::resize));
            m.add(item("Set as root", () -> actions.setRoot(r.node())));
            m.add(moveMenu(null, r.node()));
            m.addSeparator();
            m.add(item("Delete node", actions::delete));
        } else if (row instanceof FolderRow f) {
            m.addSeparator();
            m.add(item("Rename folder…", this::editSelected));
            m.add(moveMenu(f.path(), null));
            m.addSeparator();
            m.add(item("Delete folder…", () -> actions.deleteFolder(f.path())));
        }
        m.show(tree, e.getX(), e.getY());
    }

    private static String in(String folder) {
        return folder.isEmpty() ? "" : " in " + Project.nameOf(folder);
    }

    /** "Move to" submenu for a folder or a node. */
    private JMenu moveMenu(String folder, Node node) {
        JMenu menu = new JMenu("Move to");
        List<String> targets = new ArrayList<>();
        targets.add("");
        targets.addAll(ed.project().folders());
        String current = folder != null ? Project.parentOf(folder) : node.folder;
        for (String t : targets) {
            boolean ok = !t.equals(current) && (folder == null || !Project.isInside(t, folder));
            JMenuItem item = new JMenuItem(t.isEmpty() ? "(top level)" : t);
            item.setEnabled(ok);
            item.addActionListener(e -> {
                if (folder != null) moveFolder(folder, t);
                else actions.moveNodeTo(node, t);
            });
            menu.add(item);
        }
        return menu;
    }

    private void moveFolder(String folder, String newParent) {
        String target = Project.join(newParent, Project.nameOf(folder));
        if (actions.moveFolderInto(folder, newParent)) {
            remapCollapsed(folder, target);
            selectFolder(target);
        }
    }

    private static JMenuItem item(String text, Runnable r) {
        JMenuItem i = new JMenuItem(text);
        i.addActionListener(e -> r.run());
        return i;
    }

    // ---- building ----

    /** Changes whenever the library's shape changes; drawing on a node does not change it. */
    private static String structureKey(Project p) {
        StringBuilder sb = new StringBuilder(String.join("|", p.folders())).append('#').append(p.root);
        for (Node n : p.nodes) {
            sb.append('#').append(System.identityHashCode(n)).append(n.ref()).append('@').append(n.folder)
                    .append(':').append(n.size()).append(n.root != null);
        }
        return sb.toString();
    }

    private void refresh() {
        Project p = ed.project();
        String key = structureKey(p);
        if (!key.equals(lastKey) || p != lastProject) {
            lastKey = key;
            lastProject = p;
            rebuild(p);
        }
        syncSelection();
        tree.repaint();
    }

    private void rebuild(Project p) {
        String keepFolder = selectedFolder();
        syncing = true;
        try {
            DefaultMutableTreeNode root = new DefaultMutableTreeNode(new FolderRow(""));
            fill(root, "", p);
            ((DefaultTreeModel) tree.getModel()).setRoot(root);
            expandOpenFolders(root);
        } finally {
            syncing = false;
        }
        if (keepFolder != null && p.hasFolder(keepFolder)) selectFolder(keepFolder);
        else lastSynced = null;
    }

    private void fill(DefaultMutableTreeNode parent, String folder, Project p) {
        for (String sub : p.subfolders(folder)) {
            DefaultMutableTreeNode f = new DefaultMutableTreeNode(new FolderRow(sub));
            fill(f, sub, p);
            parent.add(f);
        }
        for (Node n : p.nodesIn(folder)) parent.add(new DefaultMutableTreeNode(new NodeRow(n), false));
    }

    /** Expands every folder the user has not collapsed, as long as its parents are open too. */
    private void expandOpenFolders(DefaultMutableTreeNode root) {
        for (Enumeration<TreeNode> en = root.preorderEnumeration(); en.hasMoreElements(); ) {
            DefaultMutableTreeNode n = (DefaultMutableTreeNode) en.nextElement();
            if (!(n.getUserObject() instanceof FolderRow f) || f.path().isEmpty()) continue;
            boolean open = true;
            for (String a = f.path(); !a.isEmpty(); a = Project.parentOf(a)) if (collapsed.contains(a)) open = false;
            if (open) tree.expandPath(new TreePath(n.getPath()));
        }
    }

    private TreePath pathOfFolder(String folder) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        for (Enumeration<TreeNode> en = root.preorderEnumeration(); en.hasMoreElements(); ) {
            DefaultMutableTreeNode n = (DefaultMutableTreeNode) en.nextElement();
            if (n.getUserObject() instanceof FolderRow f && f.path().equals(folder)) return new TreePath(n.getPath());
        }
        return null;
    }

    private void selectFolder(String folder) {
        TreePath p = pathOfFolder(folder);
        if (p == null) return;
        syncing = true;
        try {
            tree.setSelectionPath(p);
            tree.scrollPathToVisible(p);
        } finally {
            syncing = false;
        }
        lastSynced = ed.node();
    }

    /** Selects the node being edited, unless the user picked a folder and the node has not changed since. */
    private void syncSelection() {
        Node current = ed.node();
        Object sel = rowOf(tree.getSelectionPath());
        if (sel instanceof NodeRow r && r.node() == current) {
            lastSynced = current;
            return;
        }
        if (sel instanceof FolderRow && current == lastSynced) return;
        syncing = true;
        try {
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
            for (Enumeration<TreeNode> en = root.preorderEnumeration(); en.hasMoreElements(); ) {
                DefaultMutableTreeNode n = (DefaultMutableTreeNode) en.nextElement();
                if (n.getUserObject() instanceof NodeRow r && r.node() == current) {
                    TreePath p = new TreePath(n.getPath());
                    tree.expandPath(p.getParentPath());
                    tree.setSelectionPath(p);
                    tree.scrollPathToVisible(p);
                    lastSynced = current;
                    return;
                }
            }
            tree.clearSelection();
        } finally {
            syncing = false;
        }
    }

    // ---- drag and drop ----

    /** Drags carry "node:<index>" or "folder:<path>". Dropping on a folder files the item there; elsewhere, the top level. */
    private final class Dnd extends TransferHandler {
        @Override
        public int getSourceActions(JComponent c) {
            return MOVE;
        }

        @Override
        protected Transferable createTransferable(JComponent c) {
            Object row = rowOf(tree.getSelectionPath());
            if (row instanceof NodeRow r) return new StringSelection("node:" + ed.project().nodes.indexOf(r.node()));
            if (row instanceof FolderRow f) return new StringSelection("folder:" + f.path());
            return null;
        }

        private String targetFolder(TransferSupport s) {
            JTree.DropLocation loc = (JTree.DropLocation) s.getDropLocation();
            Object row = rowOf(loc.getPath());
            if (row instanceof FolderRow f) return f.path();
            if (row instanceof NodeRow r) return r.node().folder;
            return "";
        }

        private String payload(TransferSupport s) {
            try {
                return (String) s.getTransferable().getTransferData(DataFlavor.stringFlavor);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public boolean canImport(TransferSupport s) {
            if (!s.isDrop() || !s.isDataFlavorSupported(DataFlavor.stringFlavor)) return false;
            String data = payload(s);
            if (data == null) return false;
            String target = targetFolder(s);
            if (data.startsWith("folder:")) {
                String folder = data.substring(7);
                return !Project.isInside(target, folder) && !Project.parentOf(folder).equals(target);
            }
            return data.startsWith("node:");
        }

        @Override
        public boolean importData(TransferSupport s) {
            if (!canImport(s)) return false;
            String data = payload(s);
            String target = targetFolder(s);
            if (data.startsWith("folder:")) {
                moveFolder(data.substring(7), target);
                return true;
            }
            int i = Integer.parseInt(data.substring(5));
            List<Node> nodes = ed.project().nodes;
            if (i < 0 || i >= nodes.size()) return false;
            actions.moveNodeTo(nodes.get(i), target);
            return true;
        }
    }

    // ---- rendering ----

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;");
    }

    private final class Renderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree t, Object value, boolean sel, boolean expanded,
                                                      boolean leaf, int row, boolean focus) {
            super.getTreeCellRendererComponent(t, value, sel, expanded, leaf, row, focus);
            Object o = ((DefaultMutableTreeNode) value).getUserObject();
            Project p = ed.project();
            String muted = String.format("#%06x", Draw.MUTED.getRGB() & 0xffffff);
            if (o instanceof FolderRow f) {
                long count = p.nodes.stream().filter(n -> Project.isInside(n.folder, f.path())).count();
                setText("<html>" + esc(Project.nameOf(f.path())) + "&nbsp;&nbsp;<font color='" + muted + "'>" + count
                        + "</font></html>");
                setIcon(expanded ? OPEN_ICON : CLOSED_ICON);
                setIconTextGap(4);
            } else if (o instanceof NodeRow r) {
                Node n = r.node();
                StringBuilder sb = new StringBuilder("<html>").append(esc(n.name));
                if (!n.variant.isEmpty()) sb.append(" [").append(esc(n.variant)).append("]");
                sb.append("<br><font color='").append(muted).append("'>").append(n.size()).append("px");
                if (n.ref().equals(p.root)) sb.append(" · root");
                if (n.root == null && !n.ref().equals(p.root)) sb.append(" · no root anchor");
                sb.append("</font></html>");
                setText(sb.toString());
                setIcon(Draw.nodeIcon(n, p.palette, THUMB));
                setIconTextGap(8);
            }
            return this;
        }
    }
}
