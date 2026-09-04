import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NNHanman"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        name = "鸟鸟韩漫"
        lang = "zh"
        baseUrl = "https://nnhanman.xyz"
    }
}
