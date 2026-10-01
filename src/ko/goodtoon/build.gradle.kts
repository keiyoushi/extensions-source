import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "GoodToon"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "madara"

    source {
        lang = "ko"
        baseUrl {
            mirrors(
                "https://www.goodtoon005.com",
                "https://www.goodtoon006.com",
            )
        }
    }

    deeplink {
        path("/manga/..*")
    }
}
