plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "CManga"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl {
            custom("https://cmangax17.com")
        }
    }
}
