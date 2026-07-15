import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Iqiyi"
    versionCode = 4
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        name = "爱奇艺叭嗒"
        lang = "zh-Hans"
        baseUrl = "https://www.iqiyi.com/manhua"
        id = 2198877009406729694
    }
}
