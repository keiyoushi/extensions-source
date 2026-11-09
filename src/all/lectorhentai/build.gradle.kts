import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Lector Hentai"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    listOf("es", "en", "pt-BR", "fr", "ru").forEach {
        source {
            lang = it
            baseUrl = "https://lectorhentai.com"
        }
    }

    deeplink {
        path("/manga/..*")
        path("/read/..*")
    }
}

dependencies {
    implementation(project(":lib:unpacker"))
}
