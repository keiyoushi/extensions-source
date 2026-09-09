import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "WNACG"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        name = "紳士漫畫"
        lang = "zh"
        baseUrl = "https://www.wn07.cfd"
    }
}
