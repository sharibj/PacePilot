#!/bin/sh
# Rename this scaffold to your own project + Java package.
# Usage: ./rename.sh --name my-app --package com.acme.myapp
#
# Dependency-free (POSIX sh + sed + find + git mv). Idempotent-ish:
# safe to inspect `git status` afterward and re-run on a clean checkout.
set -eu

OLD_NAME="scaffold-fullstack-java"
OLD_PKG="com.example.app"
NEW_NAME=""
NEW_PKG=""

usage() {
  echo "Usage: $0 --name <project-name> --package <java.package>"
  echo "Example: $0 --name my-app --package com.acme.myapp"
  exit 1
}

while [ $# -gt 0 ]; do
  case "$1" in
    --name) NEW_NAME="$2"; shift 2 ;;
    --package) NEW_PKG="$2"; shift 2 ;;
    *) usage ;;
  esac
done

[ -n "$NEW_NAME" ] || usage
[ -n "$NEW_PKG" ] || usage

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

OLD_PKG_PATH="$(echo "$OLD_PKG" | tr '.' '/')"
NEW_PKG_PATH="$(echo "$NEW_PKG" | tr '.' '/')"

echo "Renaming project '$OLD_NAME' -> '$NEW_NAME'"
echo "Renaming package '$OLD_PKG' -> '$NEW_PKG'"

# 1. Replace the Java package string in all Java + gradle files.
find backend/src -name '*.java' -type f -exec sed -i.bak "s/${OLD_PKG}/${NEW_PKG}/g" {} +
sed -i.bak "s/${OLD_PKG}/${NEW_PKG}/g" backend/build.gradle

# 2. Move the package directory tree (main + test).
for base in backend/src/main/java backend/src/test/java; do
  if [ -d "$base/$OLD_PKG_PATH" ]; then
    mkdir -p "$base/$(dirname "$NEW_PKG_PATH")"
    if command -v git >/dev/null 2>&1 && git rev-parse --git-dir >/dev/null 2>&1; then
      git mv "$base/$OLD_PKG_PATH" "$base/$NEW_PKG_PATH"
    else
      mv "$base/$OLD_PKG_PATH" "$base/$NEW_PKG_PATH"
    fi
  fi
done

# 3. Replace the project name in settings.gradle, README, compose, package.json, index.html.
for f in backend/settings.gradle README.md docker-compose.yml frontend/package.json frontend/index.html; do
  [ -f "$f" ] && sed -i.bak "s/${OLD_NAME}/${NEW_NAME}/g" "$f"
done

# 4. Clean up sed backup files.
find . -name '*.bak' -type f -delete

cat <<EOF

Done. Manual follow-ups (edit .env — not touched by this script):
  - DB_NAME / DB_USER / DB_PASSWORD
  - Ports if 8080 / 5173 / 5432 / 4000 collide with something else
  - LLM_MODEL must match a model_name in litellm/litellm_config.yaml

Then verify: cd backend && ./gradlew build
EOF
