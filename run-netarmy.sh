#!/bin/bash

# NetArmyKn1f3 Launcher Script with macOS Dock Icon Support
# This script runs the application with proper JVM arguments for dock icon

echo "Starting NetArmyKn1f3 with custom dock icon..."

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Set the dock icon and name for macOS
JAVA_OPTS="-Xdock:icon=$SCRIPT_DIR/net_icon.png -Xdock:name=NetArmyKn1f3"

# Run Maven with the JVM options
mvn exec:java -Dexec.mainClass="edu.au.cpsc.module7.App" -Dexec.args="$JAVA_OPTS" 