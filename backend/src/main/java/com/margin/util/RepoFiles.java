package com.margin;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/** Shared helpers for walking and safely reading a cloned repository. */
public final class RepoFiles {
    public static final Set<String> SKIP_DIRS = Set.of(".git", "node_modules", "target", "build", "dist", "out",
            ".idea", ".vscode", "vendor", "__pycache__", ".next", ".gradle", "venv", ".venv", "coverage");
    private static final Set<String> EXT = Set.of("java", "kt", "js", "jsx", "ts", "tsx", "py", "go", "rs", "rb",
            "php", "cs", "c", "h", "cpp", "hpp", "swift", "scala", "sql", "html", "css", "scss", "xml", "yml",
            "yaml", "json", "md", "properties", "gradle", "toml", "sh", "vue", "svelte");

    private RepoFiles() {}

    public static List<Path> sourceFiles(Path root) throws IOException {
        List<Path> out = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes a) {
                boolean skip = !dir.equals(root) && SKIP_DIRS.contains(dir.getFileName().toString());
                return skip ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                String name = f.getFileName().toString();
                int dot = name.lastIndexOf('.');
                String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
                if (a.size() < 150_000 && EXT.contains(ext) && !name.endsWith(".min.js")
                        && !name.equals("package-lock.json")) {
                    out.add(f);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        Collections.sort(out);
        return out;
    }

    /** Resolves a repo-relative path and refuses anything that escapes the repo folder. */
    public static Path resolve(Path root, String rel) {
        Path p = root.resolve(rel == null ? "" : rel.replaceFirst("^/+", "")).normalize();
        if (!p.startsWith(root)) throw new IllegalArgumentException("Path is outside the repository");
        return p;
    }

    public static String rel(Path root, Path p) {
        return root.relativize(p).toString().replace('\\', '/');
    }

    /** Recursive delete that also clears read-only flags (git objects on Windows). */
    public static void purge(Path dir) {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                p.toFile().setWritable(true);
                try { Files.deleteIfExists(p); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }
}
