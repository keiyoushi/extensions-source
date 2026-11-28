import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "XXX Yaoi"
    versionCode = 78
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "pt-BR"
        baseUrl = "https://3xyaoi.com"
        versionId = 2
    }

    deeplink {
        path("/bl/..*")
    }
}
