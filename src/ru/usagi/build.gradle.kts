plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Usagi"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "grouple"

    source {
        baseUrl {
            custom("https://web.usagi.one")
        }
        lang = "ru"
    }
}
