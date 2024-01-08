#!/bin/bash
set -e

rsync -a --delete --exclude .git --exclude .gitignore --exclude README.md ../main/repo/ .
git config --global user.email "107297513+FourTOne5@users.noreply.github.com"
git config --global user.name "FourTOne5"
git status
if [ -n "$(git status --porcelain)" ]; then
    git add .
    git commit -m "Update extensions repo"
    git push
else
    echo "No changes to commit"
fi
