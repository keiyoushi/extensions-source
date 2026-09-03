import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Gistamis House"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "zeistmanga"

    source {
        lang = "es"
        baseUrl = "https://gistamishousefansub.blogspot.com"
    }
}
