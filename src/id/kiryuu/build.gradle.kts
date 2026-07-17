import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kiryuu"
    versionCode = 52
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"
    theme = "natsuid"

    source {
        lang = "id"
        baseUrl = "https://v7.kiryuu.to"
        // Formerly "Kiryuu (WP Manga Stream)"
        id = 3639673976007021338L
    }
}
