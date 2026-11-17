import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Catharsis World"
    versionCode = 6
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "catharsis"

    source {
        lang = "es"
        baseUrl {
            custom("https://newcatharsis.dig-it.info")
        }
        versionId = 3
    }

    deeplink {
        path("/manga/..*")
    }
}
