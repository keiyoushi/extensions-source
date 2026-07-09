plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Zazhimi"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        name = "杂志迷"
        lang = "zh"
        baseUrl = "https://www.zazhimi.net"
    }
}
