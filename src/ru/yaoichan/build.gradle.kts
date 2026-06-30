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
        baseUrl("https://yaoi-chan.me") {
            withCustom = true
        }
        lang = "ru"
        id = 2466512768990363955L
    }
}
