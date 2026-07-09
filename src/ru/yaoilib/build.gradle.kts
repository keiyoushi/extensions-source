plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "YaoiLib"
    versionCode = 5
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "libgroup"

    source {
        baseUrl {
            custom("https://slashlib.me")
        }
        lang = "ru"
    }
}
