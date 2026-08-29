import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MH1234"
    versionCode = 4
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        name = "漫画1234"
        lang = "zh"
        baseUrl = "https://m.wmh1234.com"
    }
}
