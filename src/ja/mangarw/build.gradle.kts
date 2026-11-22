import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Rw"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "ja"
        baseUrl = "https://mangarw.com"
    }
}
