import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Lector Asteria"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "moonlighttl"

    source {
        lang = "es"
        baseUrl = "https://visor.chifa-tong.online"
    }
}
