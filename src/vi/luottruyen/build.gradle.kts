plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "LuotTruyen"
    versionCode = 6
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl("https://luottruyen11.com") {
            withCustom = true
        }
    }
}
