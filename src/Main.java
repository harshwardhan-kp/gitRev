import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Smart File Organizer - entry point.
 *
 * Usage:  java Main [folder] [--dry-run] [--yes]
 * If no folder is given, you can paste or drag-and-drop one into the terminal.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        Scanner input = new Scanner(System.in);
        boolean dryRun = List.of(args).contains("--dry-run");
        boolean skipConfirm = List.of(args).contains("--yes");

        Path folder = getFolder(args, input);
        if (folder == null) return;

        // 1. Scan
        System.out.println("Scanning " + folder + " ...");
        List<FileInfo> files = new FolderScanner().scan(folder);
        if (files.isEmpty()) {
            System.out.println("No files found.");
            return;
        }

        // 2. Find duplicates by hash
        List<List<FileInfo>> duplicates = new DuplicateFinder().findDuplicates(files);

        // 3. Categorize with AI
        String apiKey = System.getenv("OPENAI_API_KEY");
        String model = System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4o-mini");
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("OPENAI_API_KEY not set - using simple extension rules instead of AI.");
        } else {
            System.out.println("Asking AI (" + model + ") to categorize " + files.size() + " file(s)...");
        }
        Map<FileInfo, String> categories = new AiCategorizer(apiKey, model).categorize(files);

        // In each duplicate group keep the first file; the extra copies go to "Duplicates".
        for (List<FileInfo> group : duplicates) {
            for (FileInfo extra : group.subList(1, group.size())) {
                categories.put(extra, "Duplicates");
            }
        }

        // 4. Show results
        StatsPrinter printer = new StatsPrinter();
        printer.print(files, categories, duplicates);
        printer.printPlan(categories);

        // 5. Move files (after confirmation)
        if (dryRun) {
            System.out.println("\nDry run: no files were moved.");
            return;
        }
        if (!skipConfirm) {
            System.out.print("\nMove files into these folders? (y/n): ");
            if (!input.nextLine().trim().equalsIgnoreCase("y")) {
                System.out.println("Cancelled. Nothing was moved.");
                return;
            }
        }
        new FileOrganizer(folder).organize(categories);
        System.out.println("Done! Files organized in " + folder);
    }

    /** Takes the folder from the arguments, or asks the user to paste / drag one in. */
    private static Path getFolder(String[] args, Scanner input) {
        String raw = null;
        for (String arg : args) {
            if (!arg.startsWith("--")) raw = arg;
        }
        if (raw == null) {
            System.out.print("Drag a folder here (or paste its path) and press Enter: ");
            raw = input.nextLine();
        }

        // Drag-and-drop often adds quotes or escaped spaces - clean them up.
        raw = raw.trim().replaceAll("^['\"]|['\"]$", "").replace("\\ ", " ");
        Path folder = Path.of(raw).toAbsolutePath().normalize();

        if (!Files.isDirectory(folder)) {
            System.out.println("Not a folder: " + folder);
            return null;
        }
        return folder;
    }
}
