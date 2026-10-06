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
 *   java Build.java compile | test | run | jar | dist | clean
 *
 * jar writes build/compozart.jar. dist builds the app folder for this OS in dist/<os>/compozart/ and archives it.
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
            case "dist" -> dist();
            case "clean" -> clean();
            default -> {
                usage();
                System.exit(2);
            }
        }
    }

    static void usage() {
        System.out.println("usage: java Build.java <compile|test|run|jar|dist|clean> [args]");
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
        Files.createDirectories(BUILD);
        Path out = BUILD.resolve(NAME + ".jar");
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

    /**
     * The app as a folder, plus an archive of it:
     *
     *   dist/<os>/compozart/
     *     compozart            run script (compozart.cmd on Windows)
     *     app/compozart.jar
     *     app/runtime/         trimmed Java runtime from jlink
     *     settings/            filled in on first launch
     *     compositions/        projects, starting with the demo; backups/ inside
     *
     * Only the run script and the runtime differ between platforms. The archive is a .tar.gz, which keeps the
     * executable bits on the script and runtime, or a .zip on Windows.
     */
    static void dist() throws Exception {
        Path jar = jar();
        ToolProvider jlink = tool("jlink");
        if (!Files.isDirectory(Path.of(System.getProperty("java.home"), "jmods"))
                && Runtime.version().feature() < 24) {
            throw new IllegalStateException("dist needs a JDK with a jmods/ directory (e.g. openjdk-21-jdk); "
                    + "this one is " + System.getProperty("java.home"));
        }
        String os = osName();
        Path platform = DIST.resolve(os);
        deleteTree(platform);
        Path top = platform.resolve(NAME);
        Path app = top.resolve("app");
        Files.createDirectories(app);
        Files.createDirectories(top.resolve("settings"));
        Files.createDirectories(top.resolve("compositions").resolve("backups"));
        Files.copy(jar, app.resolve(NAME + ".jar"));
        try (Stream<Path> examples = Files.list(ROOT.resolve("examples"))) {
            for (Path e : examples.filter(p -> p.toString().endsWith(".zart")).toList()) {
                Files.copy(e, top.resolve("compositions").resolve(e.getFileName()));
            }
        }

        String compress = Runtime.version().feature() >= 21 ? "zip-6" : "2";
        exec(jlink, "--add-modules", "java.desktop", "--strip-debug", "--no-header-files",
                "--no-man-pages", "--compress=" + compress, "--output", app.resolve("runtime").toString());

        if (os.equals("windows")) {
            Files.writeString(top.resolve(NAME + ".cmd"), String.join("\r\n",
                    "@echo off",
                    "rem Starts compozart with the bundled Java runtime, or an installed Java 21+ if it is missing.",
                    "setlocal",
                    "set \"HERE=%~dp0\"",
                    "set \"JAVA=%HERE%app\\runtime\\bin\\javaw.exe\"",
                    "if not exist \"%JAVA%\" set \"JAVA=javaw\"",
                    "set \"COMPOZART_HOME=%HERE%\"",
                    "start \"\" \"%JAVA%\" -jar \"%HERE%app\\" + NAME + ".jar\" %*",
                    ""));
        } else {
            Path script = top.resolve(NAME);
            Files.writeString(script, String.join("\n",
                    "#!/bin/sh",
                    "# Starts compozart with the bundled Java runtime, or an installed Java 21+ if it is missing.",
                    "here=$(cd \"$(dirname \"$0\")\" && pwd)",
                    "java=\"$here/app/runtime/bin/java\"",
                    "[ -x \"$java\" ] || java=java",
                    "COMPOZART_HOME=\"$here\"",
                    "export COMPOZART_HOME",
                    "exec \"$java\" -jar \"$here/app/" + NAME + ".jar\" \"$@\"",
                    ""));
            try {
                Files.setPosixFilePermissions(script, java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x"));
            } catch (UnsupportedOperationException ignored) {
                // not a POSIX file system; the script still runs with sh
            }
        }

        Path archive = DIST.resolve(NAME + "-" + os + (os.equals("windows") ? ".zip" : ".tar.gz"));
        if (os.equals("windows")) writeZip(top, archive);
        else writeTarGz(top, archive);
        System.out.printf("dist -> %s/ and %s (%.1f MB)%n", ROOT.relativize(top), ROOT.relativize(archive),
                Files.size(archive) / 1e6);
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
