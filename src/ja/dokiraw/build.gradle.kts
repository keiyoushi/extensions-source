import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Dokiraw"
    versionCode = 17
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "ja"
        baseUrl {
            custom("https://dokiraw.cam")
        }
    }

    deeplink {
        path("/manga/..*")
    }
}
