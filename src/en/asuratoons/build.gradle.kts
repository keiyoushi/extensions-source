import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Asuratoons (unoriginal)"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://www.asuratoons.info"
    }

    deeplink {
        host("www.asuratoons.info")
        host("asuratoons.info")
        path("/manga/..*")
        path("/chapter/..*")
    }
}
