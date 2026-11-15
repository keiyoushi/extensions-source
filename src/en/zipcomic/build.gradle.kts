import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ZipComic"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://www.zipcomic.com"
    }

    deeplink {
        host("www.zipcomic.com")
        host("zipcomic.com")
        path("/..*")
    }
}
