package compozart.ui;

import compozart.compose.Composer;
import compozart.compose.Composition;
import compozart.io.ProjectIO;
import compozart.model.Project;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * The start screen: new project, open, the most recently used projects, and the other projects in the
 * compositions folder.
 */
public final class HomeWindow extends JFrame {
    private static final int THUMB = 56;

    private final Settings settings = new Settings();
    private final Recent recent = new Recent();
    private final Path compositions = AppHome.compositionsDir();
    private final DefaultListModel<Path> recentModel = new DefaultListModel<>();
    private final DefaultListModel<Path> otherModel = new DefaultListModel<>();
    private final JList<Path> recentList = new JList<>(recentModel);
    private final JList<Path> otherList = new JList<>(otherModel);
    private final JLabel recentEmpty = new JLabel("No recent projects yet.");
    private final JLabel otherEmpty = new JLabel();
    /** Rendered thumbnails by path, filled in the background. */
    private final Map<Path, Icon> thumbs = new ConcurrentHashMap<>();

    public HomeWindow() {
        super("compozart");
        settings.load(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JLabel title = new JLabel("compozart");
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() * 2f));
        JLabel subtitle = new JLabel("Pixel art creatures, built from parts.");
        subtitle.setForeground(Draw.MUTED);
        JPanel titles = new JPanel(new GridLayout(2, 1));
        titles.setOpaque(false);
        titles.add(title);
        titles.add(subtitle);

        JButton newButton = new JButton("New project", PixelIcon.of("new"));
        newButton.addActionListener(e -> openEditor(Project.createDefault(), null));
        JButton openButton = new JButton("Open…");
        openButton.addActionListener(e -> browse());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        buttons.add(newButton);
        buttons.add(openButton);

        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(18, 20, 12, 20));
        header.add(titles, BorderLayout.WEST);
        header.add(buttons, BorderLayout.EAST);

        JPanel lists = new WidthTrackingPanel();
        lists.setLayout(new BoxLayout(lists, BoxLayout.Y_AXIS));
        lists.setBorder(BorderFactory.createEmptyBorder(0, 20, 12, 20));
        lists.add(section("Recent", recentList, recentEmpty, true));
        lists.add(Box.createVerticalStrut(14));
        lists.add(section("In the compositions folder", otherList, otherEmpty, false));
        lists.add(Box.createVerticalGlue());
        JScrollPane scroll = new JScrollPane(lists, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JLabel where = new JLabel("Compositions folder: " + compositions);
        where.setForeground(Draw.MUTED);
        JButton openFolder = new JButton("Open folder");
        openFolder.addActionListener(e -> openFolder());
        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.setBorder(BorderFactory.createEmptyBorder(8, 20, 12, 20));
        footer.add(where, BorderLayout.CENTER);
        footer.add(openFolder, BorderLayout.EAST);

        getContentPane().add(header, BorderLayout.NORTH);
        getContentPane().add(scroll, BorderLayout.CENTER);
        getContentPane().add(footer, BorderLayout.SOUTH);
        addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                String look = Appearance.get().reloadIfChanged();
                if (look != null) SwingUtilities.updateComponentTreeUI(HomeWindow.this);
            }
        });
        refresh();
        setSize(820, 620);
        setLocationRelativeTo(null);
    }

    private JPanel section(String name, JList<Path> list, JLabel empty, boolean isRecent) {
        JLabel label = new JLabel(name);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        empty.setForeground(Draw.MUTED);
        empty.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        list.setCellRenderer(new Renderer());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(-1);
        list.addListSelectionListener(e -> {
            if (!list.isSelectionEmpty()) (list == recentList ? otherList : recentList).clearSelection();
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) openSelected(list);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                popup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                popup(e);
            }

            private void popup(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int i = list.locationToIndex(e.getPoint());
                if (i < 0 || !list.getCellBounds(i, i).contains(e.getPoint())) return;
                list.setSelectedIndex(i);
                JPopupMenu m = new JPopupMenu();
                JMenuItem open = new JMenuItem("Open");
                open.addActionListener(a -> openSelected(list));
                m.add(open);
                if (isRecent) {
                    JMenuItem forget = new JMenuItem("Remove from recent");
                    forget.addActionListener(a -> forgetSelected());
                    m.add(forget);
                }
                m.show(list, e.getX(), e.getY());
            }
        });
        list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "open");
        list.getActionMap().put("open", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                openSelected(list);
            }
        });
        if (isRecent) {
            list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "forget");
            list.getActionMap().put("forget", new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    forgetSelected();
                }
            });
        }
        // A section is as tall as its content, so extra window height collects below the lists.
        JPanel p = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        p.setOpaque(false);
        p.add(label, BorderLayout.NORTH);
        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(list, BorderLayout.NORTH);
        body.add(empty, BorderLayout.CENTER);
        p.add(body, BorderLayout.CENTER);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    // ---- data ----

    /** Fills both lists: recent projects first, then any other project in the compositions folder. */
    private void refresh() {
        List<Path> recentPaths = recent.existing(settings.recentProjects);
        recentModel.clear();
        recentPaths.forEach(recentModel::addElement);
        otherModel.clear();
        for (Path p : projectsIn(compositions)) if (!recentPaths.contains(p)) otherModel.addElement(p);
        recentList.setVisible(!recentModel.isEmpty());
        recentEmpty.setVisible(recentModel.isEmpty());
        otherList.setVisible(!otherModel.isEmpty());
        otherEmpty.setText(recentPaths.isEmpty() ? "No projects here yet. New projects are saved here by default."
                : "No other projects here.");
        otherEmpty.setVisible(otherModel.isEmpty());
        if (!recentModel.isEmpty()) recentList.setSelectedIndex(0);
        else if (!otherModel.isEmpty()) otherList.setSelectedIndex(0);
        loadThumbnails();
    }

    /** Project files in a folder and its subfolders, newest first, skipping the backups. */
    static List<Path> projectsIn(Path dir) {
        if (!Files.isDirectory(dir)) return List.of();
        Path backups = dir.resolve("backups");
        try (Stream<Path> s = Files.walk(dir, 4)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> !p.startsWith(backups))
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.endsWith(ProjectIO.EXTENSION) || n.endsWith(ProjectIO.LEGACY_EXTENSION);
                    })
                    .map(p -> p.toAbsolutePath().normalize())
                    .sorted(Comparator.comparingLong(HomeWindow::modified).reversed())
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static long modified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    /** Renders each project's creature in the background and repaints as thumbnails arrive. */
    private void loadThumbnails() {
        List<Path> todo = new ArrayList<>();
        for (int i = 0; i < recentModel.size(); i++) todo.add(recentModel.get(i));
        for (int i = 0; i < otherModel.size(); i++) todo.add(otherModel.get(i));
        Thread t = new Thread(() -> {
            for (Path p : todo) {
                if (thumbs.containsKey(p)) continue;
                thumbs.put(p, thumbnail(p));
                SwingUtilities.invokeLater(() -> {
                    recentList.repaint();
                    otherList.repaint();
                });
            }
        }, "thumbnails");
        t.setDaemon(true);
        t.start();
    }

    private static Icon thumbnail(Path p) {
        BufferedImage out = new BufferedImage(THUMB, THUMB, BufferedImage.TYPE_INT_ARGB);
        try {
            Project project = ProjectIO.load(p);
            Composition c = Composer.compose(project);
            if (!c.isEmpty()) {
                BufferedImage img = Draw.image(c.flat, c.width, c.height, project.palette);
                int side = Math.max(c.width, c.height);
                Graphics2D g = out.createGraphics();
                // whole-pixel scaling when the creature fits, smooth shrinking when it does not
                if (side <= THUMB) {
                    int s = THUMB / side;
                    g.drawImage(img, (THUMB - c.width * s) / 2, (THUMB - c.height * s) / 2, c.width * s, c.height * s, null);
                } else {
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    int w = c.width * THUMB / side, h = c.height * THUMB / side;
                    g.drawImage(img, (THUMB - w) / 2, (THUMB - h) / 2, w, h, null);
                }
                g.dispose();
            }
        } catch (IOException | RuntimeException e) {
            // an unreadable file just has no picture
        }
        return new ImageIcon(out);
    }

    // ---- actions ----

    private void openSelected(JList<Path> list) {
        Path p = list.getSelectedValue();
        if (p == null) return;
        try {
            openEditor(ProjectIO.load(p), p);
        } catch (IOException | RuntimeException ex) {
            JOptionPane.showMessageDialog(this, "Could not open " + p.getFileName() + ":\n" + ex.getMessage(),
                    "Open", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void forgetSelected() {
        Path p = recentList.getSelectedValue();
        if (p == null) return;
        recent.remove(p);
        refresh();
    }

    private void browse() {
        FileDialog fd = new FileDialog(this, "Open project", FileDialog.LOAD);
        fd.setDirectory(compositions.toString());
        fd.setFilenameFilter((dir, name) -> name.endsWith(ProjectIO.EXTENSION) || name.endsWith(ProjectIO.LEGACY_EXTENSION)
                || new java.io.File(dir, name).isDirectory());
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            fd.setFile("*" + ProjectIO.EXTENSION + ";*" + ProjectIO.LEGACY_EXTENSION);
        }
        fd.setVisible(true);
        if (fd.getFile() == null) return;
        Path p = Path.of(fd.getDirectory(), fd.getFile());
        try {
            openEditor(ProjectIO.load(p), p);
        } catch (IOException | RuntimeException ex) {
            JOptionPane.showMessageDialog(this, "Could not open " + p.getFileName() + ":\n" + ex.getMessage(),
                    "Open", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openEditor(Project p, Path file) {
        MainWindow w = new MainWindow(p, file);
        w.setVisible(true);
        dispose();
    }

    private void openFolder() {
        try {
            Files.createDirectories(compositions);
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(compositions.toFile());
                return;
            }
        } catch (IOException | RuntimeException ignored) {
            // fall through and show the path instead
        }
        JOptionPane.showMessageDialog(this, "Projects are in:\n" + compositions, "Compositions", JOptionPane.INFORMATION_MESSAGE);
    }

    // ---- rendering ----

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    static String when(long millis) {
        LocalDateTime t = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZoneId.systemDefault());
        LocalDate today = LocalDate.now();
        if (t.toLocalDate().equals(today)) return "today " + TIME.format(t);
        if (t.toLocalDate().equals(today.minusDays(1))) return "yesterday " + TIME.format(t);
        return DATE.format(t);
    }

    private String folderText(Path p) {
        Path dir = p.getParent();
        if (dir == null) return "";
        if (dir.startsWith(compositions)) {
            Path rel = compositions.relativize(dir);
            return rel.toString().isEmpty() ? "compositions" : "compositions/" + rel.toString().replace('\\', '/');
        }
        String home = System.getProperty("user.home");
        String s = dir.toString();
        if (home != null && s.startsWith(home)) s = "~" + s.substring(home.length());
        return shorten(s.replace('\\', '/'), 60);
    }

    /** Long folders keep their last few names: ".../games/creatures/drafts". */
    static String shorten(String path, int max) {
        if (path.length() <= max) return path;
        String[] parts = path.split("/");
        StringBuilder tail = new StringBuilder();
        for (int i = parts.length - 1; i >= 0; i--) {
            String next = parts[i] + (tail.length() == 0 ? "" : "/" + tail);
            if (next.length() + 4 > max && tail.length() > 0) break;
            tail = new StringBuilder(next);
        }
        return "\u2026/" + tail;
    }

    /** Lays the lists out at the scroll pane's width instead of their widest text. */
    private static final class WidthTrackingPanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) {
            return r.height;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private final class Renderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean sel, boolean focus) {
            super.getListCellRendererComponent(l, value, index, sel, focus);
            Path p = (Path) value;
            String name = p.getFileName().toString();
            for (String ext : List.of(ProjectIO.EXTENSION, ProjectIO.LEGACY_EXTENSION)) {
                if (name.endsWith(ext)) name = name.substring(0, name.length() - ext.length());
            }
            String muted = String.format("#%06x", Draw.MUTED.getRGB() & 0xffffff);
            setText("<html><b>" + esc(name) + "</b><br><font color='" + muted + "'>" + esc(folderText(p))
                    + " · " + when(modified(p)) + "</font></html>");
            Icon thumb = thumbs.get(p);
            setIcon(new Icon() {
                @Override
                public void paintIcon(Component c, Graphics g, int x, int y) {
                    Draw.checker((Graphics2D) g, x, y, THUMB, THUMB, 7);
                    if (thumb != null) thumb.paintIcon(c, g, x, y);
                }

                @Override
                public int getIconWidth() {
                    return THUMB;
                }

                @Override
                public int getIconHeight() {
                    return THUMB;
                }
            });
            setIconTextGap(12);
            setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
            return this;
        }
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;");
    }
}
