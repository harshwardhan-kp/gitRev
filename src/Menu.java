import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;

/** The interactive menu shown when `organize` is run without a folder. */
public class Menu {

    private final Scanner input;
    private Path lastFolder;
    private boolean deep;

    public Menu(Scanner input) {
        this.input = input;
    }

    public void run() throws Exception {
        System.out.println("\n  Smart File Organizer " + Main.VERSION);
        System.out.println("  " + aiStatus());

        while (true) {
            System.out.println("""

                    What do you want to do?
                      1) Organize a folder      (shows the plan, then asks before moving)
                      2) Preview only           (dry run, moves nothing)
                      3) Find duplicate files
                      4) Help
                      0) Exit""");
            System.out.println("  Deep mode: " + (deep ? "ON - AI reads file contents" : "off - AI sees names only")
                    + "   (type d to toggle)");
            String choice = ask("Choose [0-4, d]: ");
            if (choice == null) return; // input closed (Ctrl+D)

            switch (choice) {
                case "1" -> withFolder(folder -> Main.organize(folder, input, false, false, deep));
                case "2" -> withFolder(folder -> Main.organize(folder, input, true, false, deep));
                case "d", "D" -> toggleDeep();
                case "3" -> withFolder(this::showDuplicates);
                case "4" -> Main.printHelp();
                case "0", "q", "exit" -> {
                    System.out.println("Bye!");
                    return;
                }
                default -> System.out.println("Please type a number from the list.");
            }
        }
    }

    private void toggleDeep() {
        deep = !deep;
        if (deep) {
            System.out.println("Deep mode ON: samples of your files (text, Office docs, images, PDFs up to 4 MB)\n"
                    + "are sent to OpenAI. Slower and costs more - don't use it on private folders.");
        } else {
            System.out.println("Deep mode off: only file names are sent.");
        }
    }

    private interface FolderAction {
        void run(Path folder) throws Exception;
    }

    /** Asks for a folder (offering the last one used), then runs the action on it. */
    private void withFolder(FolderAction action) {
        String hint = lastFolder == null ? "" : " [Enter = " + lastFolder + "]";
        String raw = ask("Drag a folder here (or paste its path)" + hint + ": ");
        if (raw == null) return;
        if (raw.isEmpty()) {
            if (lastFolder == null) {
                System.out.println("No folder given.");
                return;
            }
            raw = lastFolder.toString();
        }

        Path folder = Main.toFolder(raw);
        if (folder == null) return;
        lastFolder = folder;
        try {
            action.run(folder);
        } catch (Exception e) {
            System.out.println("Something went wrong: " + e.getMessage());
        }
    }

    private void showDuplicates(Path folder) throws Exception {
        System.out.println("Scanning " + folder + " ...");
        FolderScanner scanner = new FolderScanner();
        List<FileInfo> files = scanner.scan(folder);
        Main.printSkipped(folder, scanner.skipped());
        List<List<FileInfo>> duplicates = new DuplicateFinder().findDuplicates(files);
        if (duplicates.isEmpty()) {
            System.out.println("No duplicates among " + files.size() + " file(s).");
            return;
        }
        new StatsPrinter(folder).printDuplicates(duplicates);
    }

    private String aiStatus() {
        String key = System.getenv("OPENAI_API_KEY");
        return key == null || key.isBlank()
                ? "AI: off (set OPENAI_API_KEY to enable) - sorting by file extension"
                : "AI: on (" + System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4o-mini") + ")";
    }

    /** Prints a prompt and returns the trimmed answer, or null if input was closed. */
    private String ask(String prompt) {
        System.out.print(prompt);
        return input.hasNextLine() ? input.nextLine().trim() : null;
    }
}
