import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Zegra"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "comiciviewer"

    source {
        lang = "ja"
        baseUrl = "https://manga-zegra.com"
    }
}
