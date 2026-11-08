import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangapoi"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "zmanga"

    source {
        lang = "id"
        baseUrl {
            custom("https://mangapoi.io")
        }
    }

    deeplink {
        path("/series/..*")
    }
}
