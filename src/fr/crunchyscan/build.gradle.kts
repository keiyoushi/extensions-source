import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "CrunchyScan"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "fr"
        // The site keeps moving to new domains (.fr, then .st).
        baseUrl {
            custom("https://crunchyscan.st")
        }
    }

    deeplink {
        path("/..*")
    }
}
