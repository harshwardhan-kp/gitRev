import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Walks a folder and collects info (name, size, SHA-256 hash) for every file.
 * Leaves alone anything that would break if its files were moved: hidden folders
 * (.git, .obsidian, ...), dependency/build folders, and folders that are code projects.
 */
public class FolderScanner {

    private static final Set<String> SKIP_FOLDERS = Set.of(
            "node_modules", "dist", "build", "out", "target", "venv", "__pycache__", "Pods");

    private final List<Path> skipped = new ArrayList<>();

    public List<FileInfo> scan(Path folder) throws IOException {
        List<FileInfo> files = new ArrayList<>();
        Files.walkFileTree(folder, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (dir.equals(folder)) return FileVisitResult.CONTINUE;
                String name = dir.getFileName().toString();
                boolean isProject = Files.exists(dir.resolve(".git"));
                if (name.startsWith(".") || SKIP_FOLDERS.contains(name) || isProject || name.endsWith(".app")) {
                    skipped.add(dir);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                String name = path.getFileName().toString();
                if (attrs.isRegularFile() && !name.startsWith(".")) { // skip hidden files like .DS_Store
                    files.add(new FileInfo(path, name, extensionOf(name), attrs.size(), hashOf(path)));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path path, IOException e) {
                return FileVisitResult.CONTINUE; // no permission etc. - just skip it
            }
        });
        return files;
    }

    /** Folders that were left untouched (hidden, build output, or code projects). */
    public List<Path> skipped() {
        return skipped;
    }

    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1).toLowerCase() : "";
    }

    /** SHA-256 of the file contents. Identical files always get identical hashes. */
    private String hashOf(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
