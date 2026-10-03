import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "YSK Comics"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    listOf("ar", "en").forEach {
        source {
            lang = it
            baseUrl = "https://www.ysk-comics.com"
        }
    }

    deeplink {
        host("www.ysk-comics.com")
        path("/ar/comic/..*")
        path("/en/comic/..*")
    }
}
