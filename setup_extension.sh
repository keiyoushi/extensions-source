#!/usr/bin/env bash
set -euo pipefail

# Usage: ./setup_extension.sh [lang module]
# Example: ./setup_extension.sh id lunaranime

lang="${1:-id}"
module="${2:-Lunaranime}"

# normalize
module_lower="$(echo "$module" | tr '[:upper:]' '[:lower:]')"
first_char_upper="$(echo "${module_lower:0:1}" | tr '[:lower:]' '[:upper:]')"
class_name="${first_char_upper}${module_lower:1}"

module_dir="src/${lang}/${module_lower}"

kotlin_file="${module_dir}/src/eu/kanade/tachiyomi/extension/${lang}/${module_lower}/${class_name}.kt"

# prefer build.gradle, but accept build.gradle.kts
if [ -f "${module_dir}/build.gradle" ]; then
  gradle_file="${module_dir}/build.gradle"
elif [ -f "${module_dir}/build.gradle.kts" ]; then
  gradle_file="${module_dir}/build.gradle.kts"
else
  gradle_file=""
fi

commit_message="Update ${class_name} extension"

files_to_add=()

if [ -f "${kotlin_file}" ]; then
  files_to_add+=("${kotlin_file}")
else
  echo "⚠️  Kotlin file not found: ${kotlin_file}"
fi

if [ -n "${gradle_file}" ] && [ -f "${gradle_file}" ]; then
  files_to_add+=("${gradle_file}")
else
  echo "⚠️  build file not found in ${module_dir}"
fi

if [ ${#files_to_add[@]} -eq 0 ]; then
  echo "No files to add — nothing to commit. Create the module files first or pass correct args."
  exit 1
fi

# Stage and commit only existing files
git add "${files_to_add[@]}"

git commit -m "${commit_message}" || echo "Nothing to commit (no changes staged)"

# Optional: push
# git push origin main
