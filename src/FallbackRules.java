/** Simple extension-based categories, used when AI is not available. */
public class FallbackRules {

    public static String categoryFor(String ext) {
        return switch (ext) {
            case "pdf", "doc", "docx", "txt", "md", "odt", "rtf" -> "Documents";
            case "ppt", "pptx", "key" -> "Presentations";
            case "xls", "xlsx", "csv" -> "Spreadsheets";
            case "jpg", "jpeg", "png", "gif", "bmp", "svg", "webp", "heic" -> "Images";
            case "mp4", "mkv", "mov", "avi", "webm" -> "Videos";
            case "mp3", "wav", "flac", "aac", "m4a" -> "Music";
            case "zip", "rar", "7z", "tar", "gz" -> "Archives";
            case "exe", "msi", "dmg", "pkg", "apk", "deb", "appimage" -> "Installers & Apps";
            case "java", "py", "js", "ts", "c", "cpp", "html", "css", "json", "xml" -> "Code";
            default -> "Other";
        };
    }
}
