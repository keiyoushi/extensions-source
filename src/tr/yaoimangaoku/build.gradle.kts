import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Yaoi Manga Oku"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "madara"

    source {
        lang = "tr"
        baseUrl = "https://yaoimangaoku.net"
    }
}
