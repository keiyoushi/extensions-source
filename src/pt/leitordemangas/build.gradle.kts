import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Leitor de Mangas"
    versionCode = 52
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "aurora"

    source {
        lang = "pt-BR"
        baseUrl = "https://leitordemangas.com"
        versionId = 2
    }
}
