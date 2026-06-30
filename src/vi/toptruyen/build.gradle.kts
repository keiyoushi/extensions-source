plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Top Truyen"
    versionCode = 30
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "wpcomics"

    source {
        lang = "vi"
        baseUrl("https://www.toptruyenzone5.com") {
            withCustom = true
        }
    }
}
