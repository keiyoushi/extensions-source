import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "LeerManhwas"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "yiimanga"

    source {
        baseUrl = "https://leermanhwas.com"
        lang = "es"
    }

    deeplink {
        path("/manhwa/..*")
    }
}
