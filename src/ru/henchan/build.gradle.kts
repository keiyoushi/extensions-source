plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HenChan"
    versionCode = 41
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "multichan"

    source {
        baseUrl("https://xxl.hentaichan.live") {
            withCustom = true
        }
        lang = "ru"
        id = 5504588601186153612L
    }
}
