plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ThienThaiTruyen"
    versionCode = 5
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl = "https://thienthaitruyen11.com"
    }
}
