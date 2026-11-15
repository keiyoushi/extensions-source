import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hiperdex"
    versionCode = 81
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "hiper"

    source {
        lang = "en"
        baseUrl {
            custom("https://hiperdex.tv")
        }
    }
}
