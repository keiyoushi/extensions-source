plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Top Truyen"
    versionCode = 31
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"
    theme = "wpcomics"

    source {
        lang = "vi"
        baseUrl {
            custom("https://www.toptruyenzone6.com")
        }
    }
}
