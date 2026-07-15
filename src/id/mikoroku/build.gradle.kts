import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MikoRoku"
    versionCode = 6
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"
    theme = "zeistmanga"

    source {
        lang = "id"
        baseUrl = "https://www.mikoroku.com"
    }
}
