plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangahub"
    versionCode = 23
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        baseUrl {
            custom("https://mangahub.ru")
        }
        lang = "ru"
    }
}
