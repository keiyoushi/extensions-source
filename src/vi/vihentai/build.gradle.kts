import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ViHentai"
    versionCode = 6
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl = "https://vi-hentai.pro"
    }
}
