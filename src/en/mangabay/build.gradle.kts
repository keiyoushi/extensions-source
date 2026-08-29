import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga-Bay"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://read.manga-bay.org"
    }

    deeplink {
        host("read.manga-bay.org")
        path("/..*-..*\\.html")
    }
}
