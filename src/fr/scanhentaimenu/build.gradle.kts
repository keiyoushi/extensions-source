import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "X-Manga"
    versionCode = 60
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "fr"
        baseUrl = "https://x-manga.org"
        versionId = 2
    }
}
