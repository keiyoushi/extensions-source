import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "GALAX Scans"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "zeistmanga"

    source {
        lang = "pt-BR"
        baseUrl = "https://galaxscanlator.blogspot.com"
    }
}
