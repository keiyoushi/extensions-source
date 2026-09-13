import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ViHentai"
    versionCode = 5
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl {
            mirrors(
                "https://vi-hentai.moe",
                "https://vi-hentai.pro",
            )
        }
    }

    deeplink {
        path("/truyen/.*")
    }
}
