plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MeoSua"
    versionCode = 3
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl = "https://meosua.org"
    }
}
