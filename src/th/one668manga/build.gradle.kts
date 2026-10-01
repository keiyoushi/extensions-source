import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "1668Manga"
    theme = "mangathemesia"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        baseUrl = "https://1668manga.com"
        lang = "th"
    }
}
