plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ViHentai"
    versionCode = 4
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl = "https://vi-hentai.moe"
    }
}
