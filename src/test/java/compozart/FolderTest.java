package compozart;

import compozart.compose.Composer;
import compozart.io.ProjectIO;
import compozart.model.*;

import java.util.List;

import static compozart.test.Check.*;

public class FolderTest {
    static Node node(Project p, String name, String folder) {
        Node n = new Node(name, "", 4);
        n.folder = folder;
        p.nodes.add(n);
        return n;
    }

    static Project sample() {
        Project p = new Project(Palette.catppuccinMocha());
        node(p, "body", "");
        node(p, "eye", "head/eyes");
        node(p, "mouth", "head");
        node(p, "tail", "back");
        p.addFolder("empty");
        return p;
    }

    public void testFoldersIncludeImpliedParents() {
        eq(List.of("back", "empty", "head", "head/eyes"), sample().folders());
        eq(List.of("head/eyes"), sample().subfolders("head"));
        eq(List.of("back", "empty", "head"), sample().subfolders(""));
    }

    public void testLibraryOrderIsFoldersFirstThenNodes() {
        eq(List.of("tail", "eye", "mouth", "body"), sample().libraryOrder().stream().map(n -> n.name).toList());
    }

    public void testRenameMovesContents() {
        Project p = sample();
        p.moveFolder("head", "face");
        eq(List.of("back", "empty", "face", "face/eyes"), p.folders());
        eq("face/eyes", p.find("eye", "").folder);
        eq("face", p.find("mouth", "").folder);
    }

    public void testMoveIntoAnotherFolderAndMerge() {
        Project p = sample();
        p.moveFolder("head/eyes", "back/eyes");
        eq("back/eyes", p.find("eye", "").folder);
        eq(List.of("back", "back/eyes", "empty", "head"), p.folders());
        p.addFolder("back/head");
        p.moveFolder("head", "back/head"); // merges into the existing folder
        eq("back/head", p.find("mouth", "").folder);
    }

    public void testCannotMoveIntoItself() {
        Project p = sample();
        throwsA(IllegalArgumentException.class, () -> p.moveFolder("head", "head/eyes/deeper"));
        throwsA(IllegalArgumentException.class, () -> p.moveFolder("head", ""));
    }

    public void testDeleteMovesContentsUp() {
        Project p = sample();
        p.deleteFolder("head");
        eq("eyes", p.find("eye", "").folder);
        eq("", p.find("mouth", "").folder);
        eq(List.of("back", "empty", "eyes"), p.folders());
    }

    public void testNormalize() {
        eq("a/b", Project.normalizeFolder(" a / b "));
        eq("", Project.normalizeFolder("  "));
        throwsA(IllegalArgumentException.class, () -> Project.normalizeFolder("a//b"));
        throwsA(IllegalArgumentException.class, () -> Project.normalizeFolder("/a"));
    }

    public void testSaveRoundTripKeepsEmptyFolders() {
        Project p = sample();
        Project q = ProjectIO.fromJson(ProjectIO.toJson(p));
        eq(p.folders(), q.folders());
        eq("head/eyes", q.find("eye", "").folder);
        eq(ProjectIO.toJson(p), ProjectIO.toJson(q));
    }

    public void testOldFilesLoadAtTopLevel() {
        String json = ProjectIO.toJson(sample()).replaceAll("\\s*\"folders\": \\[[^\\]]*\\],", "")
                .replaceAll("\\s*\"folder\": \"[^\"]*\",", "");
        Project q = ProjectIO.fromJson(json);
        eq(List.of(), q.folders());
        eq("", q.find("eye", "").folder);
    }

    public void testFoldersDoNotAffectComposition() {
        Project p = new Project(Palette.catppuccinMocha());
        Node body = node(p, "body", "");
        body.anchors.add(new NamedAnchor("eye", 1, 1, Dir.N));
        Node eye = node(p, "eye", "deep/inside");
        eye.root = new RootAnchor(0, 0, Dir.S);
        p.root = body.ref();
        eq(2, Composer.compose(p).instances.size());
    }

    public void testCopyKeepsFolders() {
        Project p = sample();
        Project c = p.copy();
        c.moveFolder("head", "x");
        eq("head/eyes", p.find("eye", "").folder);
        eq(List.of("back", "empty", "head", "head/eyes"), p.folders());
    }
}
