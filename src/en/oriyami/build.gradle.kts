import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "OriYami"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://oriyami.com"
    }

    deeplink {
        path("/manga/..*")
        path("/manhwa/..*")
        path("/manhua/..*")
        path("/digital-comic/..*")
    }
}
