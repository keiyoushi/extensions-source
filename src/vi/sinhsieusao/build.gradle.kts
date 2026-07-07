plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "SinhSieuSao"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl {
            custom("https://sinhsieusao.com")
        }
    }
}
