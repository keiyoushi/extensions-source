import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "GoodToon"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "madara"

    source {
        lang = "ko"
        baseUrl {
            custom("https://www.goodtoon007.com")
        }
    }

    deeplink {
        path("/manga/..*")
    }
}
