plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "DocTruyen3Q"
    versionCode = 27
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "wpcomics"

    source {
        lang = "vi"
        baseUrl {
            custom("https://doctruyen3qhub2.com")
        }
    }
}
