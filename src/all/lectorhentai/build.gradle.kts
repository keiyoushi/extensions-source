import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Lector Hentai"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "mangathemesia"

    listOf("es", "en", "pt-BR", "fr", "ru").forEach {
        source {
            lang = it
            baseUrl = "https://lectorhentai.com"
        }
    }
}

dependencies {
    implementation(project(":lib:unpacker"))
}
