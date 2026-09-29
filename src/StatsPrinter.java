import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Prints a readable summary of the scan. */
public class StatsPrinter {

    public void print(List<FileInfo> files, Map<FileInfo, String> categories, List<List<FileInfo>> duplicates) {
        long totalBytes = files.stream().mapToLong(FileInfo::size).sum();

        System.out.println("\n========== SUMMARY ==========");
        System.out.println("Total files : " + files.size());
        System.out.println("Total size  : " + readableSize(totalBytes));

        // Count files per category (TreeMap keeps them sorted alphabetically).
        Map<String, Integer> counts = new TreeMap<>();
        for (String category : categories.values()) {
            counts.merge(category, 1, Integer::sum);
        }
        System.out.println("\nCategories:");
        counts.forEach((category, count) -> System.out.printf("  %-22s %d file(s)%n", category, count));

        System.out.println("\nDuplicates: " + duplicates.size() + " group(s)");
        long wasted = 0;
        for (List<FileInfo> group : duplicates) {
            System.out.println("  Same content (" + readableSize(group.get(0).size()) + "):");
            for (FileInfo file : group) {
                System.out.println("    - " + file.path());
            }
            wasted += group.get(0).size() * (group.size() - 1);
        }
        if (wasted > 0) {
            System.out.println("  Space used by extra copies: " + readableSize(wasted));
        }
        System.out.println("=============================\n");
    }

    public void printPlan(Map<FileInfo, String> categories) {
        System.out.println("\nPlanned categories:");
        new TreeMap<>(invert(categories)).forEach((category, names) ->
                System.out.println("  [" + category + "] " + String.join(", ", names)));
    }

    private Map<String, List<String>> invert(Map<FileInfo, String> categories) {
        Map<String, List<String>> byCategory = new TreeMap<>();
        categories.forEach((file, category) ->
                byCategory.computeIfAbsent(category, c -> new java.util.ArrayList<>()).add(file.name()));
        return byCategory;
    }

    private String readableSize(long bytes) {
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
