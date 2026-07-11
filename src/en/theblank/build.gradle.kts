plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "The Blank"
    versionCode = 54
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "pam"

    source {
        lang = "en"
        baseUrl = "https://theblank.net"
        versionId = 2
    }
}
