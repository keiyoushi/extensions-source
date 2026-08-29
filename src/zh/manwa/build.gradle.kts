import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manwa"
    versionCode = 14
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        name = "漫蛙"
        lang = "zh"
        baseUrl = "https://manwa.me"
    }
}
