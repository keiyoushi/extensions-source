plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "YKMH"
    versionCode = 18
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "sinmh"

    source {
        name = "优酷漫画"
        lang = "zh"
        baseUrl = "https://www.ykmh.net"
        id = 1637952806167036168L
    }
}
