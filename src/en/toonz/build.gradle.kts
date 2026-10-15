import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Toonz/Toonx"
    versionCode = 3
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "Toonz"
        baseUrl = "https://toonz.to"
        lang = "en"
    }

    source {
        name = "Toonx"
        baseUrl = "https://toonx.to"
        lang = "en"
    }

    deeplink {
        host("toonz.to")
        path("/manhwa/..*")
        path("/manhua/..*")
        path("/manga/..*")
        path("/western/..*")
    }

    deeplink {
        host("toonx.to")
        path("/comic/..*")
        path("/adult/..*")
    }
}
