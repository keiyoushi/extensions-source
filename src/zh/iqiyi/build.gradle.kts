import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Iqiyi"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        name = "爱奇艺叭嗒"
        lang = "zh-Hans"
        baseUrl = "https://bud.m.iqiyi.com"
        id = 2198877009406729694
    }
}
