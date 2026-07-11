plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HenChan"
    versionCode = 41
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "multichan"

    source {
        baseUrl {
            custom("https://xxl.hentaichan.live")
        }
        lang = "ru"
        id = 5504588601186153612L
    }
}
