plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "VlogTruyen"
    versionCode = 29
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl("https://vlogtruyen69.com") {
            withCustom = true
        }
        id = 6425642624422299254
    }
}
