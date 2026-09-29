import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Moves each file into the folder named after its category. */
public class FileOrganizer {

    private final Path root;

    public FileOrganizer(Path root) {
        this.root = root;
    }

    public void organize(Map<FileInfo, String> categories) throws IOException {
        for (Map.Entry<FileInfo, String> entry : categories.entrySet()) {
            moveInto(entry.getKey(), entry.getValue());
        }
    }

    private void moveInto(FileInfo file, String folderName) throws IOException {
        Path folder = root.resolve(folderName);
        Files.createDirectories(folder);
        Path target = uniqueTarget(folder, file.name());
        if (!file.path().equals(target)) {
            Files.move(file.path(), target);
        }
    }

    /** If "notes.pdf" already exists, use "notes (1).pdf", "notes (2).pdf", ... */
    private Path uniqueTarget(Path folder, String name) {
        Path target = folder.resolve(name);
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int n = 1; Files.exists(target); n++) {
            target = folder.resolve(base + " (" + n + ")" + ext);
        }
        return target;
    }
}
