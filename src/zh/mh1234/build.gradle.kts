import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MH1234"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        name = "漫画1234"
        lang = "zh"
        baseUrl = "https://m.wmh1234.com"
    }
}
