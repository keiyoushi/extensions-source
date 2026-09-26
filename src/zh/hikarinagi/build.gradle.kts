import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hikarinagi"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    // Keeps its original name, and so its original id: the manga section is the one users
    // already had in their libraries.
    source {
        name = "Hikarinagi"
        lang = "zh"
        baseUrl = "https://www.hikarinagi.org"
    }

    source {
        name = "Hikarinagi Novels"
        lang = "zh"
        baseUrl = "https://www.hikarinagi.org"
    }
}
