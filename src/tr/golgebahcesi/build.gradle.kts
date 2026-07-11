import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Gölge Bahçesi"
    versionCode = 33
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "tr"
        baseUrl = "https://golgebahcesi.com"
        versionId = 2
    }
}
