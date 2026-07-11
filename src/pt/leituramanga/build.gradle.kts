import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Leitura Manga"
    versionCode = 6
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        name = "Leitura Mangá"
        lang = "pt-BR"
        baseUrl = "https://leituramanga.net"
    }
}
