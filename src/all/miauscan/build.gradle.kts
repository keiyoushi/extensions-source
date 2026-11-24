import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Miau Scan"
    versionCode = 8
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "mangathemesia"

    listOf("es", "pt-BR").forEach { sourceLang ->
        source {
            lang = sourceLang
            baseUrl = "https://leer.lectovela.com"
        }
    }
}
