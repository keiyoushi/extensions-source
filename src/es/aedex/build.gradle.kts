import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Aedex"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "catharsis"

    source {
        lang = "es"
        baseUrl = "https://aedexnox.com"
    }

    deeplink {
        path("/comic/..*")
    }
}
