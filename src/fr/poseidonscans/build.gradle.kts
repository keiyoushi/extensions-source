plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Poseidon Scans"
    versionCode = 51
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "fr"
        baseUrl = "https://poseidon-scans.net"
        versionId = 2
    }
}
