import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ToonLivre"
    versionCode = 88
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        name = "ToonLivre"
        baseUrl = "https://toonlivre.net"
        lang = "pt-BR"
        id = 2834885536325274328L
        versionId = 2
    }
}
