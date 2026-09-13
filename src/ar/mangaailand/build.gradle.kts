import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Ai Land"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "zeistmanga"

    source {
        lang = "ar"
        baseUrl = "https://manga-ai-land.blogspot.com"
    }
}
