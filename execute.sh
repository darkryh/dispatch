#!/bin/bash

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
BIN_FILE="$SCRIPT_DIR/dispatch-sample/build/install/dispatch-sample/bin/dispatch-sample"

echo "Building latest version..."
cd "$SCRIPT_DIR"
./gradlew :dispatch-sample:installDist --warning-mode all || {
    echo "Build failed"
    exit 1
}

if [ ! -f "$BIN_FILE" ]; then
    echo "Error: Binary not found after build!"
    exit 1
fi

echo "Launching Dispatch Sample..."
exec "$BIN_FILE" "$@"
