import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Read Fairy Tail & Edens Zero Manga Online"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangacatalog"

    source {
        lang = "en"
        baseUrl = "https://ww8.readfairytail.com"
    }
}
