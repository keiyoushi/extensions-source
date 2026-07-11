plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Pix Hentai"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "oceanwp"

    source {
        lang = "id"
        baseUrl = "https://pixhentai.com"
    }
}
