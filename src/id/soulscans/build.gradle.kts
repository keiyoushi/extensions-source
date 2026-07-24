import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Soul Scans"
    versionCode = 35
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "id"
        baseUrl = "https://v1.soulscans.asia"
        id = 8061354444776372735L
    }
}
