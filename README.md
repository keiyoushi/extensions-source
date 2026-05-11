# Violin Extensions Source

Private Mihon/Tachiyomi/Suwayomi extension source workspace.

This repository is a fork/worktree based on `keiyoushi/extensions-source` and contains the source code for custom extensions.

## Repositories

- Source repository: https://github.com/violin321/extensions-source
- Published extension repository: https://github.com/violin321/extensions
- Suwayomi/Mihon repo URL:

```text
https://raw.githubusercontent.com/violin321/extensions/repo/index.min.json
```

## Current custom extensions

### Kaixinman

- Source path: `src/zh/kaixinman/`
- Package: `eu.kanade.tachiyomi.extension.zh.kaixinman`
- Current version: `1.4.3`
- Current version code: `3`
- Base URL: `https://www.kaixinman.com`

Implemented:
- Popular manga
- Latest updates
- Search with `q + __searchtoken__`
- Manga details
- Chapter list
- Chapter page image decryption

## Build

```bash
ANDROID_HOME=/opt/android-sdk ./gradlew --no-daemon :src:zh:kaixinman:assembleDebug
```

Expected APK output:

```text
src/zh/kaixinman/build/outputs/apk/debug/tachiyomi-zh.kaixinman-v1.4.3-debug.apk
```

## Publish flow

When changing an extension:

1. Update source under `src/<lang>/<source>/`.
2. Increment `extVersionCode` in the extension `build.gradle`.
3. Build the APK.
4. Copy the APK to the publish repository `repo` branch under `apk/`.
5. Update `index.json` and `index.min.json`:
   - `apk`
   - `code`
   - `version`
6. Push the source branch to `violin321/extensions-source`.
7. Push the publish branch to `violin321/extensions`.
8. Refresh extension repositories in Suwayomi/Mihon.

## Notes

This is a private/custom extension setup. It is not affiliated with Mihon, Tachiyomi, Suwayomi, Keiyoushi, or the content providers.
