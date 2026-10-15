import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Leitura Manga"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "Leitura Mangá"
        lang = "pt-BR"
        baseUrl = "https://leituramanga.net"
    }
}
