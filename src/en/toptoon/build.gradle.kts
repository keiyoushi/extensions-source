import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Toptoon"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        baseUrl = "https://global.toptoon.com"
        lang = "en"
    }

    deeplink {
        path("/content/..*")
    }
}
