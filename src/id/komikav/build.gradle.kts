import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Komik AV"
    versionCode = 8
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "id"
        baseUrl = "https://komikav.net"
        // Kept from "Komik AV (WP Manga Stream)", later renamed "APKOMIK"
        id = 7875815514004535629L
    }
}
