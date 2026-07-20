import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Happymh"
    versionCode = 25
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        name = "嗨皮漫画"
        lang = "zh"
        baseUrl = "https://m.happymh.com"
    }
}
