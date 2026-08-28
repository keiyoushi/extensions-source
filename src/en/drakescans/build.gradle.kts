import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Drake Scans"
    versionCode = 16
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "vinetheme"

    source {
        lang = "en"
        baseUrl = "https://drakecomic.net"
        versionId = 3
    }
}
