import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaBlackCat"
    versionCode = 33
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "th"
        baseUrl = "https://mangablackcat.com"
        id = 2248402620929558947L
    }
}
