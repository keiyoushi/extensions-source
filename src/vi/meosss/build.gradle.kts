plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MeoSSS"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl("https://meosss.com") {
            withCustom = true
        }
    }
}
