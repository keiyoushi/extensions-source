import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga-Bay"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://manga-bay.biz"
    }

    deeplink {
        host("manga-bay.biz")
        host("read.manga-bay.org")
        path("/..*-..*\\.html")
    }
}
