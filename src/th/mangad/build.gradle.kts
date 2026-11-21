import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MANGA-D"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "th"
        baseUrl = "https://manga-d.com"
    }

    deeplink {
        host("manga-d.com")
        host("www.manga-d.com")
        path("/..*")
    }
}
