import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Zaimanhua"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        name = "再漫画"
        lang = "zh"
        baseUrl = "https://manhua.zaimanhua.com"
    }
}
