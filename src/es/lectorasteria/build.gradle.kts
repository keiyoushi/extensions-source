import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Lector Asteria"
    versionCode = 3
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"
    theme = "moonlighttl"

    source {
        lang = "es"
        baseUrl = "https://visor.chifa-tong.online"
    }
}
