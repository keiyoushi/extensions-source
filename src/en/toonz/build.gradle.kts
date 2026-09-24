import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Toonz"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        baseUrl = "https://toonz.to"
        lang = "en"
    }

    deeplink {
        path("/manhwa/..*")
        path("/manga/..*")
        path("/western/..*")
        path("/comic/..*")
    }
}
