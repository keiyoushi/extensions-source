import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MiMiHentai"
    versionCode = 7
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl {
            custom("https://mimihentai.net")
        }
    }

    deeplink {
        path("/truyen/..*")
    }
}
