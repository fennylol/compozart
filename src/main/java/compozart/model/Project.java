package compozart.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

/** Everything that describes one creature: the palette, the node library, the root and the seed. */
public final class Project {
    public static final int FORMAT_VERSION = 1;

    public Palette palette;
    public final List<Node> nodes = new ArrayList<>();
    /** The node at the top of the creature, or null when none is chosen. */
    public NodeRef root;
    public long seed;
    /**
     * Library folders, as paths like "head/eyes". Folders only organize the library: anchors match nodes by
     * name wherever they are filed. Folders are kept here so empty ones survive a save.
     */
    private final TreeSet<String> folders = new TreeSet<>();

    public Project(Palette palette) {
        this.palette = palette;
    }

    /** A new project: Catppuccin Mocha and a single empty "body" node as the root. */
    public static Project createDefault() {
        Project p = new Project(Palette.catppuccinMocha());
        Node body = new Node("body", "", Node.DEFAULT_SIZE);
        p.nodes.add(body);
        p.root = body.ref();
        p.seed = newSeed();
        return p;
    }

    public static long newSeed() {
        return new Random().nextInt(Integer.MAX_VALUE);
    }

    public Project copy() {
        Project p = new Project(palette.copy());
        for (Node n : nodes) p.nodes.add(n.copy());
        p.root = root;
        p.seed = seed;
        p.folders.addAll(folders);
        return p;
    }

    public Node find(NodeRef ref) {
        return ref == null ? null : find(ref.name(), ref.variant());
    }

    public Node find(String name, String variant) {
        String v = variant == null ? "" : variant;
        for (Node n : nodes) if (n.name.equals(name) && n.variant.equals(v)) return n;
        return null;
    }

    /** Every node with the given name, in library order. */
    public List<Node> named(String name) {
        List<Node> out = new ArrayList<>();
        for (Node n : nodes) if (n.name.equals(name)) out.add(n);
        return out;
    }

    public Node rootNode() {
        return find(root);
    }

    /** Applies an old-to-new palette index map to every pixel. */
    public void remapPixels(int[] map) {
        for (Node n : nodes) {
            byte[] px = n.pixels();
            for (int i = 0; i < px.length; i++) px[i] = (byte) map[px[i] & 0xff];
        }
    }

    public boolean usesColor(int index) {
        for (Node n : nodes) for (byte b : n.pixels()) if ((b & 0xff) == index) return true;
        return false;
    }

    /** Renames every reference to a node name: anchor targets and the root. */
    public void renameTarget(String oldName, String newName) {
        for (Node n : nodes) for (NamedAnchor a : n.anchors) if (a.target.equals(oldName)) a.target = newName;
        if (root != null && root.name().equals(oldName)) root = new NodeRef(newName, root.variant());
    }

    /** A name of the form {@code base}, {@code base 2}, ... that no node uses with this variant. */
    public String uniqueName(String base, String variant) {
        if (find(base, variant) == null) return base;
        for (int i = 2; ; i++) if (find(base + " " + i, variant) == null) return base + " " + i;
    }

    /** A variant name of the form {@code base}, {@code base 2}, ... unused for this node name. */
    public String uniqueVariant(String name, String base) {
        if (find(name, base) == null) return base;
        for (int i = 2; ; i++) if (find(name, base + " " + i) == null) return base + " " + i;
    }

    // ---- folders ----

    /** Every folder, including ones only implied by a node's folder path, sorted. */
    public List<String> folders() {
        TreeSet<String> all = new TreeSet<>();
        for (String f : folders) addWithParents(all, f);
        for (Node n : nodes) addWithParents(all, n.folder);
        return new ArrayList<>(all);
    }

    /** The folders directly inside {@code parent}, sorted. */
    public List<String> subfolders(String parent) {
        List<String> out = new ArrayList<>();
        for (String f : folders()) if (parentOf(f).equals(parent)) out.add(f);
        return out;
    }

    /** The nodes filed directly in {@code folder}, in project order. */
    public List<Node> nodesIn(String folder) {
        List<Node> out = new ArrayList<>();
        for (Node n : nodes) if (n.folder.equals(folder)) out.add(n);
        return out;
    }

    /** Nodes in the order the library shows them: each folder's subfolders first, then its own nodes. */
    public List<Node> libraryOrder() {
        List<Node> out = new ArrayList<>();
        collect("", out);
        return out;
    }

    private void collect(String folder, List<Node> out) {
        for (String sub : subfolders(folder)) collect(sub, out);
        out.addAll(nodesIn(folder));
    }

    public boolean hasFolder(String path) {
        return path.isEmpty() || folders().contains(path);
    }

    /** Creates a folder and any missing parents. */
    public void addFolder(String path) {
        addWithParents(folders, normalizeFolder(path));
    }

    /**
     * Moves a folder, with everything in it, to a new path. Renaming is a move within the same parent.
     * If the new path already exists, the two folders merge.
     */
    public void moveFolder(String from, String to) {
        String target = normalizeFolder(to);
        if (from.isEmpty()) throw new IllegalArgumentException("the top level cannot be moved");
        if (target.isEmpty() || isInside(target, from)) throw new IllegalArgumentException("a folder cannot move into itself");
        TreeSet<String> next = new TreeSet<>();
        for (String f : folders) next.add(isInside(f, from) ? target + f.substring(from.length()) : f);
        folders.clear();
        for (String f : next) addWithParents(folders, f);
        addWithParents(folders, target);
        for (Node n : nodes) if (isInside(n.folder, from)) n.folder = target + n.folder.substring(from.length());
    }

    /** Deletes a folder. Its nodes and subfolders move up one level, merging with anything already there. */
    public void deleteFolder(String path) {
        if (path.isEmpty()) throw new IllegalArgumentException("the top level cannot be deleted");
        String parent = parentOf(path);
        TreeSet<String> next = new TreeSet<>();
        for (String f : folders) {
            if (f.equals(path)) continue;
            next.add(isInside(f, path) ? join(parent, f.substring(path.length() + 1)) : f);
        }
        folders.clear();
        for (String f : next) addWithParents(folders, f);
        for (Node n : nodes) {
            if (n.folder.equals(path)) n.folder = parent;
            else if (isInside(n.folder, path)) n.folder = join(parent, n.folder.substring(path.length() + 1));
        }
    }

    /** True when {@code path} is {@code folder} or somewhere inside it. */
    public static boolean isInside(String path, String folder) {
        return folder.isEmpty() || path.equals(folder) || path.startsWith(folder + "/");
    }

    public static String parentOf(String path) {
        int i = path.lastIndexOf('/');
        return i < 0 ? "" : path.substring(0, i);
    }

    public static String nameOf(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    public static String join(String parent, String name) {
        return parent.isEmpty() ? name : parent + "/" + name;
    }

    /** Trims each segment of a folder path and rejects empty segments. "" stays the top level. */
    public static String normalizeFolder(String path) {
        if (path == null || path.isBlank()) return "";
        StringBuilder sb = new StringBuilder();
        for (String seg : path.split("/", -1)) {
            String t = seg.trim();
            if (t.isEmpty()) throw new IllegalArgumentException("folder path has an empty name: \"" + path + "\"");
            if (sb.length() > 0) sb.append('/');
            sb.append(t);
        }
        return sb.toString();
    }

    private static void addWithParents(TreeSet<String> set, String path) {
        for (String p = path; !p.isEmpty(); p = parentOf(p)) set.add(p);
    }
}
