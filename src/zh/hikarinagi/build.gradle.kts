import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hikarinagi"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "zh"
        baseUrl = "https://www.hikarinagi.org"
    }

    source {
        name = "Hikarinagi Novels"
        lang = "zh"
        baseUrl = "https://www.hikarinagi.org"
    }
}
