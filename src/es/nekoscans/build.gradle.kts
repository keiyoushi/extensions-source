import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NekoScans"
    versionCode = 8
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "mangathemesia"

    source {
        lang = "es"
        baseUrl = "https://nekoproject.org"
        // Theme changed from ZeistManga to MangaThemesia
        versionId = 3
    }
}
