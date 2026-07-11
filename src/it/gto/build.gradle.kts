import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "GTO The Great Site"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "pizzareader"

    source {
        lang = "it"
        baseUrl = "https://reader.gtothegreatsite.net"
        versionId = 2
    }
}
