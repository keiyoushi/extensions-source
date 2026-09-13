import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaK"
    versionCode = 31
    contentWarning = ContentWarning.MIXED
    theme = "mangak"
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://mangak.io"
        id = 5020395055978987501L
    }
}
