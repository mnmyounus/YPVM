# Gradle Wrapper Fix - Instructions

## Problem
The Gradle Wrapper JAR failed validation with checksum: `74bf0ac66dce5da853febf9e64ea6445c52b4beed6c1a5b32017277496ebcff3`

This occurs because the JAR doesn't match any known Gradle release checksums validated by GitHub Actions.

## Solution

This branch contains the fix. To complete the regeneration:

### Option 1: Run the regeneration script locally (Recommended)

```bash
# Clone/checkout the fix branch
git checkout fix/gradle-wrapper-validation

# Run the regeneration script
bash regenerate-gradle-wrapper.sh

# Commit and push
git add gradle/wrapper/
git commit -m "Regenerate Gradle Wrapper JAR with valid checksum"
git push origin fix/gradle-wrapper-validation
```

### Option 2: Manual regeneration

```bash
# Clone/checkout the fix branch
git checkout fix/gradle-wrapper-validation

# Delete the invalid JAR
rm gradle/wrapper/gradle-wrapper.jar

# Regenerate with Gradle 8.11
gradle wrapper --gradle-version=8.11

# Commit and push
git add gradle/wrapper/
git commit -m "Regenerate Gradle Wrapper JAR with valid checksum"
git push origin fix/gradle-wrapper-validation
```

## What was updated

1. ✅ `gradle-wrapper.properties` - Enhanced with `networkTimeout` configuration
2. 📝 `regenerate-gradle-wrapper.sh` - Helper script for local regeneration
3. ⏳ `gradle-wrapper.jar` - **Still needs to be regenerated locally**

## Next Steps

1. Run one of the regeneration options above
2. Push the changes to this branch
3. Create a Pull Request to merge into `main`
4. The CI/CD workflow will then validate and build successfully

## Why This Works

The official Gradle wrapper JAR generated from `gradle wrapper --gradle-version=8.11` has a known checksum that GitHub Actions validates automatically. This ensures security and build reproducibility.
