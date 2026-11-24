import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Toonjai"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "th"
        baseUrl = "https://toonjai.com"
    }

    deeplink {
        host("toonjai.com")
        host("www.toonjai.com")
        path("/content/..*")
        path("/categories/..*")
    }
}
