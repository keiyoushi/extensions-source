import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

dependencies {
    implementation(libs.kotlin.stdlib)
}

keiyoushi {
    name = "ToonLivre"
    versionCode = 89
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        name = "ToonLivre"
        baseUrl = "https://toonlivre.net"
        lang = "pt-BR"
        id = 2834885536325274328L
        versionId = 2
    }
}
