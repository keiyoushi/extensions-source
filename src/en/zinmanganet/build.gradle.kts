import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Zinmanga.net"
    versionCode = 64
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "zinmangaart"

    source {
        lang = "en"
        baseUrl = "https://www.zinmanga.net"
    }
}
