#!/bin/bash

# NetArmyKn1f3 Launcher Script
# This script launches the JavaFX application using Maven

# Get the directory where this script is located
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

# Change to the project directory
cd "$DIR"

# Check if pom.xml exists
if [ ! -f "pom.xml" ]; then
    echo "Error: pom.xml not found in $DIR"
    echo "Please run this script from the project root directory"
    exit 1
fi

# Check if Maven is available
if ! command -v mvn &> /dev/null; then
    echo "Error: Maven is not installed or not in PATH"
    echo "Please install Maven first"
    exit 1
fi

# Launch the application using Maven
echo "Starting NetArmyKn1f3 Firewall Tester..."
mvn exec:java -Dexec.mainClass="edu.au.cpsc.module7.App" 