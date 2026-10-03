import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangas.in"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "mmrcms"

    source {
        lang = "es"
        baseUrl = "https://m440.in"
    }
}

dependencies {

    implementation(project(":lib:synchrony"))
    implementation(project(":lib:cryptoaes"))
}
