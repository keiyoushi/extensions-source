plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "YaoiLib"
    versionCode = 5
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "libgroup"

    source {
        baseUrl {
            custom("https://slashlib.me")
        }
        lang = "ru"
    }
}
