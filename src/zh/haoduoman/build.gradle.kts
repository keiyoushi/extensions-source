import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Haoduoman"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        name = "好多漫"
        lang = "zh"
        baseUrl = "https://www.haoduoman.com"
    }
}
