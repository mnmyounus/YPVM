#!/bin/bash
set -e

echo "Setting up JDK 17..."
# Install Java if needed (Ubuntu system)
if ! command -v java &> /dev/null; then
    sudo apt-get update
    sudo apt-get install -y openjdk-17-jdk
fi

echo "Regenerating Gradle Wrapper for version 8.11..."
gradle wrapper --gradle-version=8.11

echo "Gradle Wrapper regenerated successfully!"
echo "Files updated:"
git status gradle/wrapper/

echo ""
echo "To commit these changes, run:"
echo "  git add gradle/wrapper/"
echo "  git commit -m 'Regenerate Gradle Wrapper JAR with valid checksum'"
echo "  git push origin fix/gradle-wrapper-validation"
