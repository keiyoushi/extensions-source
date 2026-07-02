plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangahub"
    versionCode = 23
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        baseUrl("https://mangahub.ru") {
            withCustom = true
        }
        lang = "ru"
    }
}
