# Smart File Organizer (Java CLI)

Point it at a messy folder and it will:
1. **Scan** every file (name, size, SHA-256 hash)
2. **Find duplicates** (files with the same hash have the same content)
3. **Ask AI (OpenAI)** to put each file into a category like *Academics*, *Documents*, *Installers & Apps*...
4. **Print stats**, then **move** files into category folders. Extra duplicate copies go to `Duplicates/`.

No API key? It still works, using simple extension rules instead of AI.

## Install (one time)

```bash
./install.sh          # builds the jar and adds an `organize` command to ~/.local/bin
```

Needs a JDK 17+ (`brew install openjdk@21`). Re-run `./install.sh` after changing the code.

## Use it

```bash
organize                        # interactive menu - just follow the prompts
organize ~/Downloads --dry-run  # preview only, move nothing
organize ~/Downloads            # organize (asks before moving)
organize ~/Downloads --yes      # skip the "are you sure?" question
organize ~/Downloads --deep     # AI also reads contents (text, Word/PowerPoint/Excel, images, PDFs)
organize --help
```

For AI categories: `export OPENAI_API_KEY="sk-..."` (never commit it!).
Optional: `export OPENAI_MODEL=gpt-4o` (default `gpt-4o-mini`).
Without a key it still works, using file-extension rules.

**Deep mode** (`--deep`, or `d` in the menu) sends a small sample of each file to OpenAI:
the first ~2,000 characters of text, the text inside Office files, and images/PDFs up to 4 MB
(videos and audio still go by name). It is slower and costs more, so don't use it on private folders.

**Safe by default:** hidden folders (`.git`, `.obsidian`...), `node_modules`/`dist`/`build`, apps
and any folder that is a git project are left untouched, so organizing never breaks a project.
The screen shows a short summary; the full list is saved to
`~/.local/share/smart-file-organizer/last-report.txt`.

## Code overview (`src/`)

| File | What it does |
|------|--------------|
| `Main.java` | Command-line flags, runs each step in order |
| `Menu.java` | Interactive menu shown when no folder is given |
| `FileInfo.java` | Record holding one file's details |
| `FolderScanner.java` | Walks the folder and hashes files |
| `DuplicateFinder.java` | Groups files by hash |
| `AiCategorizer.java` | Sends file names (and contents in deep mode) to OpenAI, reads back categories |
| `ContentReader.java` | Turns a file's contents into AI input: text sample, Office text, image or PDF |
| `FallbackRules.java` | Extension-based categories when AI is unavailable |
| `Json.java` | Tiny JSON helpers (so we need no libraries) |
| `StatsPrinter.java` | Prints the summary |
| `FileOrganizer.java` | Moves files into their folders |
