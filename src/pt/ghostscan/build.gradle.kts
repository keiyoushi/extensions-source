import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ghost Scan"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "madara"

    source {
        lang = "pt-BR"
        baseUrl = "https://ghostscan.xyz"
    }
}
