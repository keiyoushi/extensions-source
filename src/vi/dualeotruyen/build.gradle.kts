plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Dua Leo Truyen"
    versionCode = 24
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        name = "Dưa Leo Truyện"
        lang = "vi"
        baseUrl {
            custom("https://dualeotruyenhn.com")
        }
    }
}
