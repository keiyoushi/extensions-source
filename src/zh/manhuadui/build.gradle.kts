import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "YKMH"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "sinmh"

    source {
        name = "优酷漫画"
        lang = "zh"
        baseUrl = "https://www.ykmh.net"
        id = 1637952806167036168L
    }
}
