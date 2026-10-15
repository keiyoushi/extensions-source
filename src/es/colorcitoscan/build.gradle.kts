import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Colorcito Scan"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://coloresito.site"
    }

    source {
        name = "Colorcito Toons"
        lang = "es"
        baseUrl = "https://colorcitotoons.site"
    }

    deeplink {
        path("/ver/..*")
    }
}
