import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Prints a short, readable summary: paths relative to the scanned folder, long lists cut down,
 * and nothing wider than the terminal. The complete list goes to a report file.
 */
public class StatsPrinter {

    private static final int MAX_GROUPS = 10;        // duplicate groups shown on screen
    private static final int MAX_FILES_PER_LIST = 5; // files shown per group / category
    static final Path REPORT = Path.of(System.getProperty("user.home"),
            ".local", "share", "smart-file-organizer", "last-report.txt");

    private final Path root;
    private final int width = terminalWidth();

    public StatsPrinter(Path root) {
        this.root = root;
    }

    public void print(List<FileInfo> files, Map<FileInfo, String> categories, List<List<FileInfo>> duplicates) {
        long totalBytes = files.stream().mapToLong(FileInfo::size).sum();

        System.out.println("\n" + line('='));
        System.out.println(" " + files.size() + " files  ·  " + readableSize(totalBytes)
                + "  ·  " + duplicates.size() + " duplicate set(s), " + readableSize(wasted(duplicates)) + " wasted");
        System.out.println(line('='));

        // Count files per category, biggest first, with a small bar.
        Map<String, Integer> counts = new TreeMap<>();
        for (String category : categories.values()) {
            counts.merge(category, 1, Integer::sum);
        }
        int most = counts.values().stream().max(Integer::compare).orElse(1);
        System.out.println("\n Categories");
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> System.out.printf("   %-20s %5d  %s%n",
                        shorten(e.getKey(), 20), e.getValue(), "█".repeat(Math.max(1, e.getValue() * 20 / most))));

        printDuplicates(duplicates);
    }

    public void printDuplicates(List<List<FileInfo>> duplicates) {
        if (duplicates.isEmpty()) {
            System.out.println("\n No duplicates.");
            return;
        }
        // Show the groups that waste the most space first.
        List<List<FileInfo>> sorted = new ArrayList<>(duplicates);
        sorted.sort(Comparator.comparingLong((List<FileInfo> g) -> g.get(0).size() * (g.size() - 1)).reversed());

        System.out.println("\n Duplicates  (" + duplicates.size() + " sets, " + readableSize(wasted(duplicates))
                + " in extra copies" + (sorted.size() > MAX_GROUPS ? ", biggest " + MAX_GROUPS + " shown" : "") + ")");
        int n = 1;
        for (List<FileInfo> group : sorted.subList(0, Math.min(MAX_GROUPS, sorted.size()))) {
            System.out.println("\n   " + n++ + ". " + group.size() + " copies × " + readableSize(group.get(0).size()));
            Path commonFolder = commonParent(group);
            if (commonFolder != null) {
                // All copies sit in one folder: show it once, then just the names.
                System.out.println("      in " + shorten(relative(commonFolder) + "/", width - 9));
                printList(group.stream().map(FileInfo::name).toList(), "        ");
            } else {
                printList(group.stream().map(f -> relative(f.path())).toList(), "      ");
            }
        }
        if (sorted.size() > MAX_GROUPS) {
            System.out.println("\n   + " + (sorted.size() - MAX_GROUPS) + " more set(s) - see the full report");
        }
    }

    public void printPlan(Map<FileInfo, String> categories) {
        Map<String, List<FileInfo>> byCategory = invert(categories);
        System.out.println("\n Plan  (folder → files that will move there)");
        byCategory.forEach((category, files) -> {
            System.out.println("\n   " + category + "/  (" + files.size() + ")");
            printList(files.stream().map(FileInfo::name).sorted(String.CASE_INSENSITIVE_ORDER).toList(), "      ");
        });
        writeReport(byCategory);
        System.out.println("\n Full list of every file: " + REPORT);
    }

    /** One item per line, cut to the terminal width, with "+ N more" after the first few. */
    private void printList(List<String> items, String indent) {
        for (String item : items.subList(0, Math.min(MAX_FILES_PER_LIST, items.size()))) {
            System.out.println(indent + shorten(item, width - indent.length()));
        }
        if (items.size() > MAX_FILES_PER_LIST) {
            System.out.println(indent + "+ " + (items.size() - MAX_FILES_PER_LIST) + " more");
        }
    }

    /** Writes every planned move to a text file, since the screen only shows a sample. */
    private void writeReport(Map<String, List<FileInfo>> byCategory) {
        try {
            Files.createDirectories(REPORT.getParent());
            try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(REPORT))) {
                out.println("Smart File Organizer report for " + root + "\n");
                byCategory.forEach((category, files) -> {
                    out.println(category + "/  (" + files.size() + ")");
                    files.forEach(f -> out.println("    " + relative(f.path())));
                    out.println();
                });
            }
        } catch (IOException e) {
            System.out.println(" (could not write report: " + e.getMessage() + ")");
        }
    }

    private Map<String, List<FileInfo>> invert(Map<FileInfo, String> categories) {
        Map<String, List<FileInfo>> byCategory = new TreeMap<>();
        categories.forEach((file, category) ->
                byCategory.computeIfAbsent(category, c -> new ArrayList<>()).add(file));
        return byCategory;
    }

    private static Path commonParent(List<FileInfo> group) {
        Path parent = group.get(0).path().getParent();
        return group.stream().allMatch(f -> f.path().getParent().equals(parent)) ? parent : null;
    }

    /** Path shown relative to the scanned folder, e.g. "Unit3 2/notes.pdf". */
    private String relative(Path path) {
        String rel = root.relativize(path).toString();
        return rel.isEmpty() ? "." : rel;
    }

    /** Cuts the middle out of long text so both the start and the file name stay visible. */
    static String shorten(String text, int max) {
        if (max < 10 || text.length() <= max) return text;
        int keepEnd = max * 2 / 3;
        return text.substring(0, max - keepEnd - 1) + "…" + text.substring(text.length() - keepEnd);
    }

    private String line(char c) {
        return String.valueOf(c).repeat(Math.min(width, 70));
    }

    private static long wasted(List<List<FileInfo>> duplicates) {
        return duplicates.stream().mapToLong(g -> g.get(0).size() * (g.size() - 1)).sum();
    }

    /** Terminal width from $COLUMNS or `stty size`, falling back to 100. */
    private static int terminalWidth() {
        try {
            String cols = System.getenv("COLUMNS");
            if (cols != null) return Integer.parseInt(cols.trim());
            Process p = new ProcessBuilder("sh", "-c", "stty size < /dev/tty").start();
            String[] out = new String(p.getInputStream().readAllBytes()).trim().split("\\s+");
            if (p.waitFor() == 0 && out.length == 2) return Integer.parseInt(out[1]);
        } catch (Exception ignored) {
            // not a real terminal
        }
        return 100;
    }

    static String readableSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        String[] units = {"KB", "MB", "GB", "TB"};
        double size = bytes;
        int unit = -1;
        while (size >= 1024 && unit < units.length - 1) {
            size /= 1024;
            unit++;
        }
        return String.format("%.1f %s", size, units[unit]);
    }
}
