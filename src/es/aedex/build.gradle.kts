import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Aedex"
    pkgName = "es.templescanesp"
    versionCode = 74
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
