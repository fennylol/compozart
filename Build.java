import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.jar.*;
import java.util.spi.ToolProvider;
import java.util.stream.*;
import java.util.zip.*;
import javax.tools.*;

/**
 * Build script for compozart. Run with the JDK's source launcher:
 *
 *   java Build.java compile | test | run | jar | bundle | single | clean
 *
 * The jar runs on any OS and goes in dist/. Everything tied to one OS goes in dist/<os>/.
 * No Gradle or Maven. Everything here uses the JDK's own APIs, so it behaves
 * the same on Linux and Windows.
 */
public class Build {
    static final String NAME = "compozart";
    static final String MAIN_CLASS = "compozart.Main";
    static final String VERSION = "0.1.0";

    static final Path ROOT = Path.of("").toAbsolutePath();
    static final Path SRC = ROOT.resolve("src/main/java");
    static final Path RES = ROOT.resolve("src/main/resources");
    static final Path TEST_SRC = ROOT.resolve("src/test/java");
    static final Path LIB = ROOT.resolve("lib");
    static final Path BUILD = ROOT.resolve("build");
    static final Path CLASSES = BUILD.resolve("classes");
    static final Path TEST_CLASSES = BUILD.resolve("test-classes");
    static final Path DIST = ROOT.resolve("dist");

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
            return;
        }
        String task = args[0];
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (task) {
            case "compile" -> compile();
            case "test" -> System.exit(test(rest));
            case "run" -> System.exit(run(rest));
            case "jar" -> jar();
            case "bundle" -> bundle();
            case "single" -> single();
            case "clean" -> clean();
            default -> {
                usage();
                System.exit(2);
            }
        }
    }

    static void usage() {
        System.out.println("usage: java Build.java <compile|test|run|jar|bundle|single|clean> [args]");
        System.out.println("  test [Class...]  run all tests, or only the named test classes");
        System.out.println("  run [args...]    start the app, passing args through");
    }

    // ---- tasks ----

    static void compile() throws IOException {
        javac(SRC, CLASSES, libJars());
        copyTree(RES, CLASSES);
        System.out.println("compiled -> " + ROOT.relativize(CLASSES));
    }

    static int test(String[] only) throws Exception {
        compile();
        List<Path> cp = new ArrayList<>(libJars());
        cp.add(CLASSES);
        javac(TEST_SRC, TEST_CLASSES, cp);
        List<String> classes;
        if (only.length > 0) {
            classes = Arrays.stream(only).map(c -> c.contains(".") ? c : "compozart." + c).toList();
        } else {
            try (Stream<Path> s = Files.walk(TEST_CLASSES)) {
                classes = s.map(p -> TEST_CLASSES.relativize(p).toString())
                        .filter(p -> p.endsWith("Test.class") && !p.contains("$"))
                        .map(p -> p.substring(0, p.length() - 6).replace(File.separatorChar, '.'))
                        .sorted().toList();
            }
        }
        cp.add(TEST_CLASSES);
        List<String> cmd = new ArrayList<>(List.of(javaExe(), "-Djava.awt.headless=true",
                "-cp", joinPath(cp), "compozart.test.TestRunner"));
        cmd.addAll(classes);
        return new ProcessBuilder(cmd).inheritIO().start().waitFor();
    }

    static int run(String[] args) throws Exception {
        compile();
        List<Path> cp = new ArrayList<>(libJars());
        cp.add(0, CLASSES);
        List<String> cmd = new ArrayList<>(List.of(javaExe(), "-cp", joinPath(cp), MAIN_CLASS));
        cmd.addAll(List.of(args));
        return new ProcessBuilder(cmd).inheritIO().start().waitFor();
    }

    static Path jar() throws IOException {
        compile();
        Files.createDirectories(DIST);
        Path out = DIST.resolve(NAME + ".jar");
        Manifest mf = new Manifest();
        Attributes a = mf.getMainAttributes();
        a.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        a.put(Attributes.Name.MAIN_CLASS, MAIN_CLASS);
        a.put(Attributes.Name.IMPLEMENTATION_VERSION, VERSION);
        // FlatLaf ships version-specific classes under META-INF/versions/9.
        a.put(new Attributes.Name("Multi-Release"), "true");
        Set<String> seen = new HashSet<>();
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(out), mf)) {
            seen.add("META-INF/");
            seen.add("META-INF/MANIFEST.MF");
            try (Stream<Path> s = Files.walk(CLASSES)) {
                for (Path p : s.sorted().toList()) {
                    String name = CLASSES.relativize(p).toString().replace(File.separatorChar, '/');
                    if (name.isEmpty()) continue;
                    if (Files.isDirectory(p)) name += "/";
                    if (!seen.add(name)) continue;
                    jar.putNextEntry(new JarEntry(name));
                    if (!Files.isDirectory(p)) Files.copy(p, jar);
                    jar.closeEntry();
                }
            }
            for (Path lib : libJars()) {
                String libName = lib.getFileName().toString().replaceFirst("(-[0-9.]+)?\\.jar$", "");
                try (ZipInputStream in = new ZipInputStream(Files.newInputStream(lib))) {
                    for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                        String name = e.getName();
                        if (name.equals("META-INF/MANIFEST.MF") || name.endsWith("module-info.class")
                                || name.matches("META-INF/[^/]+\\.(SF|RSA|DSA|EC)")) continue;
                        if (name.equals("META-INF/LICENSE")) name = "META-INF/LICENSE-" + libName;
                        if (!seen.add(name)) continue;
                        jar.putNextEntry(new JarEntry(name));
                        in.transferTo(jar);
                        jar.closeEntry();
                    }
                }
            }
        }
        System.out.printf("jar -> %s (%.1f MB)%n", ROOT.relativize(out), Files.size(out) / 1e6);
        return out;
    }

    /** Builds the app folder (native launcher, jar, trimmed runtime) with jlink and jpackage. Returns its path. */
    static Path appImage() throws Exception {
        Path jar = jar();
        ToolProvider jlink = tool("jlink");
        ToolProvider jpackage = tool("jpackage");
        if (!Files.isDirectory(Path.of(System.getProperty("java.home"), "jmods"))
                && Runtime.version().feature() < 24) {
            throw new IllegalStateException("bundle needs a JDK with a jmods/ directory (e.g. openjdk-21-jdk); "
                    + "this one is " + System.getProperty("java.home"));
        }

        Path runtime = BUILD.resolve("runtime");
        Path input = BUILD.resolve("bundle-input");
        Path image = BUILD.resolve("bundle");
        deleteTree(runtime);
        deleteTree(input);
        deleteTree(image);
        Files.createDirectories(input);
        Files.copy(jar, input.resolve(jar.getFileName()));

        String compress = Runtime.version().feature() >= 21 ? "zip-6" : "2";
        exec(jlink, "--add-modules", "java.desktop", "--strip-debug", "--no-header-files",
                "--no-man-pages", "--compress=" + compress, "--output", runtime.toString());
        exec(jpackage, "--type", "app-image", "--name", NAME, "--app-version", VERSION,
                "--input", input.toString(), "--main-jar", jar.getFileName().toString(),
                "--main-class", MAIN_CLASS, "--runtime-image", runtime.toString(),
                "--dest", image.toString());
        return image.resolve(NAME);
    }

    /** dist/<os>/, created if needed. */
    static Path platformDist() throws IOException {
        Path dir = DIST.resolve(osName());
        Files.createDirectories(dir);
        return dir;
    }

    /** The app folder as an archive: .zip on Windows, .tar.gz elsewhere so the launcher stays executable. */
    static void bundle() throws Exception {
        Path appDir = appImage();
        String os = osName();
        Path out = platformDist().resolve(NAME + "-" + os + (os.equals("windows") ? ".zip" : ".tar.gz"));
        if (os.equals("windows")) writeZip(appDir, out);
        else writeTarGz(appDir, out);
        System.out.printf("bundle -> %s (%.1f MB)%n", ROOT.relativize(out), Files.size(out) / 1e6);
    }

    /** Size of the shell header in front of the archive. The header is padded to exactly this many bytes. */
    static final int STUB_SIZE = 4096;

    /**
     * One self-extracting executable: a shell header followed by the app folder as .tar.gz.
     * On first run it unpacks into the user's cache folder, keyed by version and content hash, then starts
     * from there. It tells the app where the file itself lives, so settings.json sits next to it.
     */
    static void single() throws Exception {
        String os = osName();
        if (os.equals("windows")) {
            throw new IllegalStateException("single is not available on Windows yet; use bundle for a zipped folder");
        }
        Path appDir = appImage();
        Path payload = BUILD.resolve("single-payload.tar.gz");
        writeTarGz(appDir, payload);
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(payload));
        String hash = HexFormat.of().formatHex(digest).substring(0, 12);

        String script = """
                #!/bin/sh
                # NAME VERSION, self-extracting build for OS.
                # The first run unpacks the app into the cache folder below; later runs start from there.
                # settings.json is kept next to this file.
                set -e
                self=$(cd "$(dirname "$0")" && pwd)/$(basename "$0")
                root="${XDG_CACHE_HOME:-$HOME/.cache}/NAME"
                dir="$root/VERSION-HASH"
                app="$dir/NAME/bin/NAME"
                if [ ! -x "$app" ]; then
                    mkdir -p "$root"
                    tmp=$(mktemp -d "$root/.unpack.XXXXXX")
                    tail -c +OFFSET "$self" | tar -xzf - -C "$tmp"
                    if [ -e "$dir" ] && [ ! -x "$app" ]; then rm -rf "$dir"; fi
                    mv "$tmp" "$dir" 2>/dev/null || true
                    # another copy may have finished first; drop ours either way
                    rm -rf "$tmp" "$dir/${tmp##*/}"
                fi
                COMPOZART_HOME=$(dirname "$self")
                export COMPOZART_HOME
                exec "$app" "$@"
                """
                .replace("NAME", NAME).replace("VERSION", VERSION).replace("HASH", hash).replace("OS", os)
                .replace("OFFSET", String.valueOf(STUB_SIZE + 1));
        byte[] head = script.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (head.length + 2 > STUB_SIZE) throw new IllegalStateException("stub script is longer than " + STUB_SIZE + " bytes");
        byte[] stub = new byte[STUB_SIZE];
        Arrays.fill(stub, (byte) ' ');
        System.arraycopy(head, 0, stub, 0, head.length);
        // The padding sits on a comment line after exec, so the shell never reads it.
        stub[head.length] = '#';
        stub[STUB_SIZE - 1] = '\n';

        Path out = platformDist().resolve(NAME);
        try (OutputStream o = Files.newOutputStream(out)) {
            o.write(stub);
            Files.copy(payload, o);
        }
        try {
            Files.setPosixFilePermissions(out, java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x"));
        } catch (UnsupportedOperationException ignored) {
            // not a POSIX file system; the file can still be run with sh
        }
        System.out.printf("single -> %s (%.1f MB)%n", ROOT.relativize(out), Files.size(out) / 1e6);
    }

    static void clean() throws IOException {
        deleteTree(BUILD);
        deleteTree(DIST);
        System.out.println("cleaned");
    }

    // ---- helpers ----

    static void javac(Path srcDir, Path outDir, List<Path> classpath) throws IOException {
        JavaCompiler javac = javax.tools.ToolProvider.getSystemJavaCompiler();
        if (javac == null) throw new IllegalStateException("no Java compiler: run Build.java with a JDK, not a JRE");
        List<File> sources;
        try (Stream<Path> s = Files.walk(srcDir)) {
            sources = s.filter(p -> p.toString().endsWith(".java")).map(Path::toFile).toList();
        }
        deleteTree(outDir);
        Files.createDirectories(outDir);
        if (sources.isEmpty()) return;
        try (StandardJavaFileManager fm = javac.getStandardFileManager(null, null, null)) {
            List<String> opts = new ArrayList<>(List.of("--release", "21", "-encoding", "UTF-8",
                    "-Xlint:all,-serial,-processing", "-d", outDir.toString()));
            if (!classpath.isEmpty()) {
                opts.add("-cp");
                opts.add(joinPath(classpath));
            }
            boolean ok = javac.getTask(null, fm, null, opts, null, fm.getJavaFileObjectsFromFiles(sources)).call();
            if (!ok) {
                System.err.println("compilation failed");
                System.exit(1);
            }
        }
    }

    static List<Path> libJars() throws IOException {
        if (!Files.isDirectory(LIB)) return List.of();
        try (Stream<Path> s = Files.list(LIB)) {
            return s.filter(p -> p.toString().endsWith(".jar")).sorted().toList();
        }
    }

    static String joinPath(List<Path> paths) {
        return paths.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator));
    }

    static String javaExe() {
        return ProcessHandle.current().info().command()
                .orElse(Path.of(System.getProperty("java.home"), "bin", "java").toString());
    }

    static ToolProvider tool(String name) {
        return ToolProvider.findFirst(name)
                .orElseThrow(() -> new IllegalStateException(name + " not found: run Build.java with a full JDK"));
    }

    static void exec(ToolProvider tool, String... args) {
        int code = tool.run(System.out, System.err, args);
        if (code != 0) throw new IllegalStateException(tool.name() + " failed with exit code " + code);
    }

    static String osName() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return "windows";
        if (os.contains("mac")) return "macos";
        return "linux";
    }

    static void copyTree(Path from, Path to) throws IOException {
        if (!Files.isDirectory(from)) return;
        try (Stream<Path> s = Files.walk(from)) {
            for (Path p : s.toList()) {
                Path dst = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(dst);
                else Files.copy(p, dst, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
        }
    }

    static void writeZip(Path dir, Path out) throws IOException {
        Files.createDirectories(out.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(out));
             Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.sorted().toList()) {
                String name = dir.getParent().relativize(p).toString().replace(File.separatorChar, '/');
                if (Files.isDirectory(p)) name += "/";
                zip.putNextEntry(new ZipEntry(name));
                if (!Files.isDirectory(p)) Files.copy(p, zip);
                zip.closeEntry();
            }
        }
    }

    /** Writes a ustar archive, keeping file modes so the launcher stays executable. */
    static void writeTarGz(Path dir, Path out) throws IOException {
        Files.createDirectories(out.getParent());
        try (OutputStream tar = new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(out)));
             Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.sorted().toList()) {
                String name = dir.getParent().relativize(p).toString().replace(File.separatorChar, '/');
                boolean isDir = Files.isDirectory(p);
                if (isDir) name += "/";
                long size = isDir ? 0 : Files.size(p);
                tar.write(tarHeader(name, mode(p, isDir), size, isDir));
                if (!isDir) {
                    Files.copy(p, tar);
                    int pad = (int) ((512 - size % 512) % 512);
                    tar.write(new byte[pad]);
                }
            }
            tar.write(new byte[1024]);
        }
    }

    static int mode(Path p, boolean isDir) {
        try {
            int m = 0;
            for (PosixFilePermission perm : Files.getPosixFilePermissions(p)) m |= 1 << (8 - perm.ordinal());
            return m;
        } catch (UnsupportedOperationException | IOException e) {
            return isDir || Files.isExecutable(p) ? 0755 : 0644;
        }
    }

    static byte[] tarHeader(String name, int mode, long size, boolean isDir) {
        byte[] h = new byte[512];
        byte[] nameBytes = name.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String prefix = "";
        if (nameBytes.length > 100) {
            // ustar splits long paths into a prefix (<= 155 bytes) and a name (<= 100 bytes) at a '/'.
            int cut = -1;
            for (int i = name.indexOf('/'); i > 0; i = name.indexOf('/', i + 1)) {
                if (i <= 155 && name.substring(i + 1).getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 100) {
                    cut = i;
                    break;
                }
            }
            if (cut < 0) throw new IllegalArgumentException("path too long for tar: " + name);
            prefix = name.substring(0, cut);
            name = name.substring(cut + 1);
        }
        put(h, 0, 100, name);
        put(h, 100, 8, String.format("%07o", mode));
        put(h, 108, 8, "0000000");
        put(h, 116, 8, "0000000");
        put(h, 124, 12, String.format("%011o", size));
        put(h, 136, 12, String.format("%011o", System.currentTimeMillis() / 1000));
        Arrays.fill(h, 148, 156, (byte) ' ');
        h[156] = (byte) (isDir ? '5' : '0');
        put(h, 257, 6, "ustar");
        put(h, 263, 2, "00");
        put(h, 345, 155, prefix);
        int sum = 0;
        for (byte b : h) sum += b & 0xff;
        put(h, 148, 8, String.format("%06o", sum) + "\0 ");
        return h;
    }

    static void put(byte[] h, int off, int len, String s) {
        byte[] b = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        System.arraycopy(b, 0, h, off, Math.min(len, b.length));
    }
}
