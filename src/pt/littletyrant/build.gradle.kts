import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Little Tyrant"
    versionCode = 12
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"
    theme = "madaralegacy"

    source {
        lang = "pt-BR"
        baseUrl = "https://tiraninha.world"
    }
}
