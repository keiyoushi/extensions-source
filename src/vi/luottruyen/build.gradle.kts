plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "LuotTruyen"
    versionCode = 5
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl("https://luottruyen10.com") {
            withCustom = true
        }
    }
}
