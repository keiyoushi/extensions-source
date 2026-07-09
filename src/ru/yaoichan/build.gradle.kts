plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "YaoiChan"
    versionCode = 5
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "multichan"

    source {
        baseUrl {
            custom("https://yaoi-chan.me")
        }
        lang = "ru"
        id = 2466512768990363955L
    }
}
