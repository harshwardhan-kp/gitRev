import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/** Walks a folder and collects info (name, size, SHA-256 hash) for every file. */
public class FolderScanner {

    public List<FileInfo> scan(Path folder) throws IOException {
        List<FileInfo> files = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(folder)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String name = path.getFileName().toString();
                if (name.startsWith(".")) {
                    continue; // skip hidden files like .DS_Store
                }
                files.add(new FileInfo(path, name, extensionOf(name), Files.size(path), hashOf(path)));
            }
        }
        return files;
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
