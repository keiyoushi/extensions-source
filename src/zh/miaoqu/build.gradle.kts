import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Miaoqu Manhua"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mccms"

    source {
        name = "喵趣漫画"
        lang = "zh"
        baseUrl = "https://www.miaoqumh.org"
    }
}
