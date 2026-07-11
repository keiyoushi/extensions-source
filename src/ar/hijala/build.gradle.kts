import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hijala"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "ar"
        baseUrl = "https://hijala.com"
        // Site moved from ZeistManga to MangaThemesia again
        versionId = 2
    }
}
