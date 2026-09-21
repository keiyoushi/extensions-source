import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Scan-Manga"
    versionCode = 25
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        baseUrl = "https://m.scan-manga.com"
        lang = "fr"
    }

    deeplink {
        host("m.scan-manga.com")
        host("www.scan-manga.com")
        path("/..*/..*\\.html")
    }
}
