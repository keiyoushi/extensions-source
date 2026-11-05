import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Komik AV"
    // Was 6 on the MangaThemesia theme (published 1.6.7), so it must stay above 7.
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
