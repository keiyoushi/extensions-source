import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Luvyaa"
    versionCode = 6
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "mangathemesia"

    source {
        lang = "id"
        baseUrl {
            custom("https://v6.luvyaa.co")
        }
    }
}
