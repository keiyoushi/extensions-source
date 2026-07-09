plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "KaliScan"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madtheme"

    source {
        lang = "en"
        baseUrl {
            mirrors(
                "https://kaliscan.com",
                "https://kaliscan.me",
                "https://kaliscan.io",
                "https://mgjinx.com",
            )
        }
        id = 7660637864742395387L
    }
}
