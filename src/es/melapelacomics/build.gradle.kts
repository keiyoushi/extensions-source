import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Melapela Comics"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://melapelacomics.com"
    }

    deeplink {
        host("melapelacomics.com")
        host("www.melapelacomics.com")
        path("/..*")
    }
}
