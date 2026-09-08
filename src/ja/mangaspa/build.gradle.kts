import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga SPA"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"
    theme = "comiciviewer"

    source {
        lang = "ja"
        baseUrl = "https://mangaspa.nikkan-spa.jp"
    }
}
