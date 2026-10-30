import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Dokiraw"
    versionCode = 18
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "ja"
        baseUrl {
            custom("https://dokiraw.city")
        }
    }

    deeplink {
        path("/manga/..*")
    }
}
