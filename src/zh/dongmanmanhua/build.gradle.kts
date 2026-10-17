import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Dongman Manhua"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "zh-Hans"
        baseUrl = "https://www.dongmanmanhua.cn"
        id = 4222375517460530289
    }
}
