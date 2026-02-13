#!/bin/bash

# Extension name
ext_name="Lunaranime"

# GitHub repository details
repo_owner="GH-Capital"
repo_name="waltzy"

# Files to commit
kotlin_file="src/id/lunaranime/Lunaranime.kt"
gradle_file="build.gradle"

# Commit message
commit_message="Update ${ext_name} extension"

# Add changes to git
git add "${kotlin_file}"
git add "${gradle_file}"

# Commit changes
git commit -m "${commit_message}"

# Push changes to the repository (optional)
# git push origin main