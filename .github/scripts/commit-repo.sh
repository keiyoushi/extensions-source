#!/bin/bash
set -e

MAIN_REVISION="$(git -C ../main rev-parse HEAD)"

rsync -a --delete --exclude .git --exclude .gitignore --exclude README.md ../main/repo/ .
git config --global user.email "156378334+keiyoushi-bot@users.noreply.github.com"
git config --global user.name "keiyoushi-bot"
git status
if [ -n "$(git status --porcelain)" ]; then
    cat > .git/COMMIT_EDITMSG << EOF
Update extensions repo

keiyoushi/extensions-source#$MAIN_REVISION
EOF

    git add .
    git commit
    git push

    curl https://purge.jsdelivr.net/gh/keiyoushi/extensions@repo/index.min.json
else
    echo "No changes to commit"
fi
