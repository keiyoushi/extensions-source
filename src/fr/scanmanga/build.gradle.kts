import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Scan-Manga"
    versionCode = 23
    contentWarning = ContentWarning.NSFW // or MIXED, please confirm
    libVersion = "1.4"

    source {
        baseUrl = "https://m.scan-manga.com"
        lang = "fr"
    }
}
