plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Zerobyw"
    versionCode = 21
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        name = "zero搬运网"
        lang = "zh"
        baseUrl {
            custom("http://www.zerobyw33.com")
        }
    }
}
