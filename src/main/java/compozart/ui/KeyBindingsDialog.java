package compozart.ui;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;

/** Lets the user rebind every action. Conflicting keys are shown in red. */
final class KeyBindingsDialog extends JDialog {
    private final KeyMap keys;
    private final List<KeyMap.Def> defs;
    private final Map<String, List<KeyStroke>> working = new HashMap<>();
    private final Model model = new Model();
    private final JTable table = new JTable(model);
    private boolean applied;

    KeyBindingsDialog(Frame owner, KeyMap keys) {
        super(owner, "Keyboard shortcuts", true);
        this.keys = keys;
        this.defs = new ArrayList<>(keys.defs());
        for (KeyMap.Def d : defs) working.put(d.id(), new ArrayList<>(keys.bindings(d.id())));

        table.setRowHeight(table.getRowHeight() + 4);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setPreferredWidth(260);
        table.getColumnModel().getColumn(1).setPreferredWidth(220);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean f, int row, int col) {
                super.getTableCellRendererComponent(t, v, sel, f, row, col);
                setForeground(col == 1 && hasConflict(defs.get(row).id()) ? Draw.PROBLEM : sel ? t.getSelectionForeground() : t.getForeground());
                setToolTipText(col == 1 && hasConflict(defs.get(row).id()) ? "Another action uses the same key" : null);
                return this;
            }
        });

        JButton add = new JButton("Add key…");
        add.addActionListener(e -> capture());
        JButton clear = new JButton("Clear");
        clear.addActionListener(e -> edit(l -> l.clear()));
        JButton reset = new JButton("Reset");
        reset.addActionListener(e -> {
            int r = table.getSelectedRow();
            if (r < 0) return;
            working.put(defs.get(r).id(), new ArrayList<>(defs.get(r).defaults()));
            model.fireTableDataChanged();
            table.setRowSelectionInterval(r, r);
        });
        JButton resetAll = new JButton("Reset all");
        resetAll.addActionListener(e -> {
            for (KeyMap.Def d : defs) working.put(d.id(), new ArrayList<>(d.defaults()));
            model.fireTableDataChanged();
        });
        JButton ok = new JButton("OK");
        ok.addActionListener(e -> {
            applied = true;
            dispose();
        });
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT));
        left.add(add);
        left.add(clear);
        left.add(reset);
        left.add(resetAll);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        right.add(ok);
        right.add(cancel);
        JPanel south = new JPanel(new BorderLayout());
        south.add(left, BorderLayout.WEST);
        south.add(right, BorderLayout.EAST);

        JLabel help = new JLabel("Select an action, then Add key… and press the new key. Double-click a row to add a key.");
        help.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        help.setForeground(Draw.MUTED);
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) capture();
            }
        });

        getContentPane().add(help, BorderLayout.NORTH);
        getContentPane().add(new JScrollPane(table), BorderLayout.CENTER);
        getContentPane().add(south, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(ok);
        setSize(620, 560);
        setLocationRelativeTo(owner);
    }

    /** Shows the dialog. Returns the new bindings, or null if cancelled. */
    Map<String, List<KeyStroke>> showDialog() {
        setVisible(true);
        return applied ? working : null;
    }

    private void edit(java.util.function.Consumer<List<KeyStroke>> change) {
        int r = table.getSelectedRow();
        if (r < 0) return;
        change.accept(working.get(defs.get(r).id()));
        model.fireTableDataChanged();
        table.setRowSelectionInterval(r, r);
    }

    private boolean hasConflict(String id) {
        for (KeyStroke ks : working.get(id)) {
            for (var e : working.entrySet()) {
                if (!e.getKey().equals(id) && e.getValue().contains(ks)) return true;
            }
        }
        return false;
    }

    private void capture() {
        int r = table.getSelectedRow();
        if (r < 0) return;
        JDialog d = new JDialog(this, "Press a key", true);
        JLabel l = new JLabel("Press the new key for “" + defs.get(r).label() + "” (Esc to cancel)", SwingConstants.CENTER);
        l.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        l.setFocusable(true);
        d.add(l);
        d.setFocusTraversalKeysEnabled(false);
        l.setFocusTraversalKeysEnabled(false);
        l.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int code = e.getKeyCode();
                if (code == KeyEvent.VK_SHIFT || code == KeyEvent.VK_CONTROL || code == KeyEvent.VK_ALT
                        || code == KeyEvent.VK_META || code == KeyEvent.VK_ALT_GRAPH || code == KeyEvent.VK_UNDEFINED) return;
                if (code == KeyEvent.VK_ESCAPE && e.getModifiersEx() == 0) {
                    d.dispose();
                    return;
                }
                KeyStroke ks = KeyStroke.getKeyStroke(code, e.getModifiersEx());
                edit(list -> {
                    if (!list.contains(ks)) list.add(ks);
                });
                d.dispose();
            }
        });
        d.pack();
        d.setLocationRelativeTo(this);
        SwingUtilities.invokeLater(l::requestFocusInWindow);
        d.setVisible(true);
    }

    private final class Model extends AbstractTableModel {
        @Override
        public int getRowCount() {
            return defs.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int c) {
            return c == 0 ? "Action" : "Keys";
        }

        @Override
        public Object getValueAt(int r, int c) {
            KeyMap.Def d = defs.get(r);
            return c == 0 ? d.label() : KeyMap.describe(working.get(d.id()));
        }
    }
}
