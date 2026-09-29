#!/usr/bin/env bash
# Builds organize.jar and installs an `organize` command into ~/.local/bin.
# Re-run it any time you change the code.
set -euo pipefail

REPO="$(cd "$(dirname "$0")" && pwd)"
APP_DIR="$HOME/.local/share/smart-file-organizer"
BIN_DIR="$HOME/.local/bin"

# Find a JDK 17+: JAVA_HOME, then Homebrew, then whatever is on PATH.
JAVA_BIN=""
for dir in "${JAVA_HOME:-}/bin" /opt/homebrew/opt/openjdk@21/bin /opt/homebrew/opt/openjdk@17/bin \
           /opt/homebrew/opt/openjdk/bin /usr/local/opt/openjdk/bin; do
    if [ -x "$dir/javac" ]; then JAVA_BIN="$dir"; break; fi
done
if [ -z "$JAVA_BIN" ] && javac -version >/dev/null 2>&1; then
    JAVA_BIN="$(dirname "$(command -v javac)")"
fi
if [ -z "$JAVA_BIN" ]; then
    echo "No JDK found. Install one with:  brew install openjdk@21" >&2
    exit 1
fi
echo "Using JDK: $JAVA_BIN"

# Compile and package.
rm -rf "$REPO/out"
"$JAVA_BIN/javac" -d "$REPO/out" "$REPO"/src/*.java
mkdir -p "$APP_DIR" "$BIN_DIR"
"$JAVA_BIN/jar" --create --file "$APP_DIR/organize.jar" --main-class Main -C "$REPO/out" .

# Launcher script.
cat > "$BIN_DIR/organize" <<EOF
#!/usr/bin/env bash
exec "$JAVA_BIN/java" -jar "$APP_DIR/organize.jar" "\$@"
EOF
chmod +x "$BIN_DIR/organize"

echo "Installed: $BIN_DIR/organize"
case ":$PATH:" in
    *":$BIN_DIR:"*) echo "Run it with:  organize" ;;
    *) echo "Add this to your ~/.zshrc, then open a new terminal:"
       echo "  export PATH=\"$BIN_DIR:\$PATH\"" ;;
esac
