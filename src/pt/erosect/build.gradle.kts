import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ero Sect"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        name = "EroSect"
        lang = "pt-BR"
        baseUrl = "https://erosect.xyz"
    }
}
