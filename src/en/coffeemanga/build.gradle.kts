import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Coffee Manga"
    theme = "madara"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        baseUrl = "https://coffeemanga.net"
        lang = "en"
    }
}
