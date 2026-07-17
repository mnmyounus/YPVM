#!/bin/bash
# Script to regenerate Gradle Wrapper for version 8.11

# Download Gradle 8.11 and generate wrapper
mkdir -p /tmp/gradle-setup
cd /tmp/gradle-setup

# Download Gradle 8.11
wget https://services.gradle.org/distributions/gradle-8.11-bin.zip
unzip gradle-8.11-bin.zip

# Generate wrapper in the project
cd - # back to project root
/tmp/gradle-setup/gradle-8.11/bin/gradle wrapper --gradle-version=8.11 --distribution-type=bin

# Clean up temp directory
rm -rf /tmp/gradle-setup

echo "Gradle Wrapper regenerated successfully!"
