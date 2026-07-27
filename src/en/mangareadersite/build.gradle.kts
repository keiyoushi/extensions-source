import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaReader.site"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "mangahub"

    source {
        lang = "en"
        baseUrl = "https://mangareader.site"
    }
}
