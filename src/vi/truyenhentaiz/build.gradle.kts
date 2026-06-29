plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "TruyenHentaiz"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl = "https://truyenhentaiz.net"
    }
}
