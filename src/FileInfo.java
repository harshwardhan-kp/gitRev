import java.nio.file.Path;

/** Everything we know about one file. */
public record FileInfo(Path path, String name, String extension, long size, String hash) {
}
