import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Uchuujin Projects"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "es"
        baseUrl = "https://uchuujinmangas.com"
    }
}
