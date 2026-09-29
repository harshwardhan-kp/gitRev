import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Turns a file's contents into OpenAI message parts (JSON), so the AI can look inside it.
 * Text is trimmed to a short sample and large files are skipped to keep requests small and cheap.
 */
public class ContentReader {

    private static final int MAX_TEXT_CHARS = 2000;
    private static final long MAX_BINARY_BYTES = 4 * 1024 * 1024; // images and PDFs

    private static final Set<String> TEXT = Set.of(
            "txt", "md", "csv", "tsv", "json", "xml", "html", "htm", "css", "log", "yaml", "yml",
            "java", "py", "js", "ts", "c", "cpp", "h", "go", "rs", "sh", "sql", "tex", "rtf");
    private static final Set<String> IMAGES = Set.of("png", "jpg", "jpeg", "webp", "gif");

    /** Returns zero or more JSON content parts describing what is inside the file. */
    public static List<String> partsFor(FileInfo file) {
        List<String> parts = new ArrayList<>();
        try {
            String ext = file.extension();
            if (TEXT.contains(ext)) {
                addText(parts, readTextSample(file));
            } else if (ext.equals("docx") || ext.equals("pptx") || ext.equals("xlsx")) {
                addText(parts, readOfficeText(file));
            } else if (IMAGES.contains(ext) && file.size() <= MAX_BINARY_BYTES) {
                String mime = "image/" + (ext.equals("jpg") ? "jpeg" : ext);
                parts.add("{\"type\":\"image_url\",\"image_url\":{\"url\":\"" + dataUrl(file, mime)
                        + "\",\"detail\":\"low\"}}");
            } else if (ext.equals("pdf") && file.size() <= MAX_BINARY_BYTES) {
                parts.add("{\"type\":\"file\",\"file\":{\"filename\":\"" + Json.escape(file.name())
                        + "\",\"file_data\":\"" + dataUrl(file, "application/pdf") + "\"}}");
            }
        } catch (IOException | RuntimeException e) {
            // Unreadable file: the AI just gets the name.
        }
        return parts;
    }

    private static void addText(List<String> parts, String text) {
        text = text.strip();
        if (text.isEmpty()) return;
        if (text.length() > MAX_TEXT_CHARS) text = text.substring(0, MAX_TEXT_CHARS) + " ...";
        parts.add("{\"type\":\"text\",\"text\":\"" + Json.escape("Contents:\n" + text) + "\"}");
    }

    private static String readTextSample(FileInfo file) throws IOException {
        try (InputStream in = Files.newInputStream(file.path())) {
            byte[] bytes = in.readNBytes(MAX_TEXT_CHARS * 2);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    /** .docx/.pptx/.xlsx are zip files full of XML - pull the text out of the XML. */
    private static String readOfficeText(FileInfo file) throws IOException {
        StringBuilder text = new StringBuilder();
        try (ZipFile zip = new ZipFile(file.path().toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements() && text.length() < MAX_TEXT_CHARS) {
                String name = entries.nextElement().getName();
                boolean wanted = name.equals("word/document.xml")
                        || name.matches("ppt/slides/slide\\d+\\.xml")
                        || name.equals("xl/sharedStrings.xml");
                if (!wanted) continue;
                try (InputStream in = zip.getInputStream(zip.getEntry(name))) {
                    String xml = new String(in.readNBytes(200_000), StandardCharsets.UTF_8);
                    text.append(xml.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ")).append('\n');
                }
            }
        }
        return text.toString();
    }

    private static String dataUrl(FileInfo file, String mime) throws IOException {
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(file.path()));
    }
}
