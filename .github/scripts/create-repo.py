name: Build extensions

on:
  push:
    branches:
      - main
  workflow_dispatch:

concurrency:
  group: ${{ github.workflow }}
  cancel-in-progress: true

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - name: Setup JDK
        uses: actions/setup-java@v4
        with:
          java-version: 17
          distribution: temurin

      - name: Generate signing key
        run: |
          keytool -genkeypair -alias waltzy -keyalg RSA -keysize 2048 -validity 10000 -keystore signingkey.jks -storepass waltzysign -keypass waltzysign -dname "CN=waltzy"

      - name: Build extensions
        run: |
          chmod +x ./gradlew
          ./gradlew assembleRelease -x spotlessCheck -x spotlessGradleCheck --no-build-cache
        env:
          KEY_STORE_PASSWORD: waltzysign
          ALIAS: waltzy
          KEY_PASSWORD: waltzysign

      - name: Prepare and generate repo
        run: |
          mkdir -p repo/apk repo/icon

          echo "=== Finding APKs ==="
          find . -name "*.apk" -path "*/release/*" -type f

          echo "=== Copying APKs ==="
          find . -name "*.apk" -path "*/release/*" -type f -exec cp {} repo/apk/ \;

          echo "=== APKs in repo/apk ==="
          ls -la repo/apk/

          APK_COUNT=$(ls repo/apk/*.apk 2>/dev/null | wc -l)
          echo "Total APKs: $APK_COUNT"

          if [ "$APK_COUNT" -eq "0" ]; then
            echo "ERROR: No APKs found!"
            exit 1
          fi

          echo "=== Generating index.min.json ==="
          python3 .github/scripts/create-repo.py

          echo "=== index.min.json content ==="
          cat repo/index.min.json

          echo ""
          echo "=== All files in repo/ ==="
          find repo -type f | head -50

      - name: Deploy repo
        run: |
          cd repo
          git init
          git config user.name "github-actions"
          git config user.email "actions@github.com"
          git checkout -b repo
          git add .
          git commit -m "Update repo"
          git remote add origin https://x-access-token:${{ secrets.GITHUB_TOKEN }}@github.com/${{ github.repository }}.git
          git push  -f origin repo
