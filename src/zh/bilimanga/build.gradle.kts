import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "BiliManga"
    versionCode = 12
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "嗶哩漫畫"
        lang = "zh"
        baseUrl = "https://www.bilimanga.net"
        id = 7289707411592168382L
    }

    deeplink {
        host("www.bilimanga.net")
        path("/detail/.*\\.html")
    }
}
