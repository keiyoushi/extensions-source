import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Toshokan Z"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        name = "マンガ図書館Z"
        lang = "ja"
        baseUrl = "https://www.mangaz.com"
    }
}
