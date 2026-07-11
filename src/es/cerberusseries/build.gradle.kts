import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Cerberus Series"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "es"
        baseUrl = "https://legionscans.com/wp"
        // Moved from custom to MangaThemesia
        versionId = 2
    }
}
