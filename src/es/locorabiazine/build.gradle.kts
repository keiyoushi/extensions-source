import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Loco Rabia Zine"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        baseUrl = "https://locorabia.myportfolio.com"
        lang = "es"
    }

    deeplink {
        path("/..*")
    }
}
