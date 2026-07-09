plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "KadoComi"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        name = "カドコミ"
        lang = "ja"
        baseUrl = "https://comic-walker.com"
    }
}
