import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "APKOMIK"
    versionCode = 6
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "id"
        baseUrl = "https://01.apkomik.com"
        // Formerly "Komik AV (WP Manga Stream)"
        id = 7875815514004535629L
    }
}
