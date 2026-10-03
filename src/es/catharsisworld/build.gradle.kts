import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Catharsis World"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

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
