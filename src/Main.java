import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Smart File Organizer - entry point.
 *
 * Usage:  organize                          -> interactive menu
 *         organize <folder> [--dry-run] [--yes] [--deep]
 */
public class Main {

    static final String VERSION = "1.1.0";

    public static void main(String[] args) throws Exception {
        List<String> flags = List.of(args);
        if (flags.contains("--help") || flags.contains("-h")) {
            printHelp();
            return;
        }
        if (flags.contains("--version")) {
            System.out.println("organize " + VERSION);
            return;
        }

        Scanner input = new Scanner(System.in);
        String rawFolder = null;
        for (String arg : args) {
            if (!arg.startsWith("-")) rawFolder = arg;
        }

        // No folder given: open the interactive menu.
        if (rawFolder == null) {
            new Menu(input).run();
            return;
        }

        Path folder = toFolder(rawFolder);
        if (folder == null) return;
        organize(folder, input, flags.contains("--dry-run"), flags.contains("--yes"), flags.contains("--deep"));
    }

    /** Scan, categorize, show the plan, then (optionally) move files. */
    static void organize(Path folder, Scanner input, boolean dryRun, boolean skipConfirm, boolean deep)
            throws Exception {
        // 1. Scan
        System.out.println("Scanning " + folder + " ...");
        FolderScanner scanner = new FolderScanner();
        List<FileInfo> files = scanner.scan(folder);
        printSkipped(folder, scanner.skipped());
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
            System.out.println("Asking AI (" + model + ") to categorize " + files.size() + " file(s)"
                    + (deep ? " by reading their contents..." : " by name..."));
        }
        Map<FileInfo, String> categories = new AiCategorizer(apiKey, model, deep).categorize(files);

        // In each duplicate group keep the first file; the extra copies go to "Duplicates".
        for (List<FileInfo> group : duplicates) {
            for (FileInfo extra : group.subList(1, group.size())) {
                categories.put(extra, "Duplicates");
            }
        }

        // 4. Show results
        StatsPrinter printer = new StatsPrinter(folder);
        printer.print(files, categories, duplicates);
        printer.printPlan(categories);

        // 5. Move files (after confirmation)
        if (dryRun) {
            System.out.println("\nDry run: no files were moved.");
            return;
        }
        if (!skipConfirm) {
            System.out.print("\nMove files into these folders? (y/n): ");
            if (!input.hasNextLine() || !input.nextLine().trim().equalsIgnoreCase("y")) {
                System.out.println("Cancelled. Nothing was moved.");
                return;
            }
        }
        new FileOrganizer(folder).organize(categories);
        System.out.println("Done! Files organized in " + folder);
    }

    /** Lists the folders the scanner left alone, so nothing is skipped silently. */
    static void printSkipped(Path root, List<Path> skipped) {
        if (skipped.isEmpty()) return;
        List<String> names = skipped.stream().map(p -> root.relativize(p) + "/").toList();
        String shown = String.join(", ", names.subList(0, Math.min(6, names.size())))
                + (names.size() > 6 ? ", + " + (names.size() - 6) + " more" : "");
        System.out.println("Left untouched (hidden, build or project folders): " + shown);
    }

    /** Cleans up a pasted / dragged-in path and checks that it is a folder. */
    static Path toFolder(String raw) {
        // Drag-and-drop often adds quotes or escaped spaces - clean them up.
        raw = raw.trim().replaceAll("^['\"]|['\"]$", "").replace("\\ ", " ");
        if (raw.startsWith("~")) raw = System.getProperty("user.home") + raw.substring(1);
        Path folder = Path.of(raw).toAbsolutePath().normalize();

        if (!Files.isDirectory(folder)) {
            System.out.println("Not a folder: " + folder);
            return null;
        }
        return folder;
    }

    static void printHelp() {
        System.out.println("""
                Smart File Organizer %s

                Usage:
                  organize                         open the interactive menu
                  organize <folder>                organize a folder (asks before moving)
                  organize <folder> --dry-run      preview only, move nothing
                  organize <folder> --yes          don't ask for confirmation
                  organize <folder> --deep         let the AI read file contents (text, Office docs,
                                                   images, PDFs); slower, costs more, and sends
                                                   those contents to OpenAI
                  organize --help | --version

                Environment:
                  OPENAI_API_KEY   enables AI categories (otherwise uses file extensions)
                  OPENAI_MODEL     model to use (default gpt-4o-mini)
                """.formatted(VERSION));
    }
}
