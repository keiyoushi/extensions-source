import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "TmoManhwa"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "yiimanga"

    source {
        lang = "es"
        baseUrl = "https://tmomanhwa.com"
    }

    deeplink {
        path("/manhwa/..*")
    }
}
