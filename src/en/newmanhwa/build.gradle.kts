import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "New Manhwa"
    versionCode = 35
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://saymanhwa.com"
    }

    deeplink {
        path("/..*")
    }
}
