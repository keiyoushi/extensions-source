plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Yuri Moon Sub"
    versionCode = 2
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "zeistmanga"

    source {
        lang = "ar"
        baseUrl = "https://yurimoonsub.blogspot.com"
    }
}
