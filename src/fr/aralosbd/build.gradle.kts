import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "AralosBD"
    versionCode = 6
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "fr"
        baseUrl = "https://aralosbd.fr"
    }
}
