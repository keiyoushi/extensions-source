#!/bin/bash
set -e

git config --global user.email "FelipeGFA@users.noreply.github.com"  # Mudança: seu email de bot
git config --global user.name "FelipeGFA-bot"  # Mudança: seu nome de bot
git status
if [ -n "$(git status --porcelain)" ]; then
    git add .
    git commit -m "Update extensions repo"
    git push

    curl https://purge.jsdelivr.net/gh/FelipeGFA/extensoes@repo/index.min.json  # Mudança: aponta para seu repo
else
    echo "No changes to commit"
fi
