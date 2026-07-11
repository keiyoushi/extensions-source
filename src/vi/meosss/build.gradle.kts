plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MeoSSS"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl {
            custom("https://meosss.com")
        }
    }
}
