This is a Directroy dedicated to revision of the Git and GitHub command and wayarounds.
# Smart File Organizer (Java CLI)

Point it at a messy folder and it will:
1. **Scan** every file (name, size, SHA-256 hash)
2. **Find duplicates** (files with the same hash have the same content)
3. **Ask AI (OpenAI)** to put each file into a category like *Academics*, *Documents*, *Installers & Apps*...
4. **Print stats**, then **move** files into category folders. Extra duplicate copies go to `Duplicates/`.

No API key? It still works, using simple extension rules instead of AI.

## Run it

```bash
javac -d out src/*.java                 # compile (Java 17+)
export OPENAI_API_KEY="sk-..."           # your key (never commit it!)
java -cp out Main                        # then drag a folder into the terminal
java -cp out Main ~/Downloads --dry-run  # preview only, move nothing
java -cp out Main ~/Downloads --yes      # skip the "are you sure?" question
```

Optional: `export OPENAI_MODEL=gpt-4o` to use a different model (default `gpt-4o-mini`).

## Code overview (`src/`)

| File | What it does |
|------|--------------|
| `Main.java` | Reads the folder, runs each step in order |
| `FileInfo.java` | Record holding one file's details |
| `FolderScanner.java` | Walks the folder and hashes files |
| `DuplicateFinder.java` | Groups files by hash |
| `AiCategorizer.java` | Sends file names to OpenAI, reads back categories |
| `FallbackRules.java` | Extension-based categories when AI is unavailable |
| `Json.java` | Tiny JSON helpers (so we need no libraries) |
| `StatsPrinter.java` | Prints the summary |
| `FileOrganizer.java` | Moves files into their folders |
