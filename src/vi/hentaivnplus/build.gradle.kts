plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiVN.plus"
    versionCode = 18
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "vi"
        baseUrl {
            custom("https://hentaivn.show")
        }
    }
}
