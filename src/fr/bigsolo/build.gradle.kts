plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "BigSolo"
    className = "BigSolo"
    versionCode = 5
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    deeplink {
        host("bigsolo.org")
        host("www.bigsolo.org")
        path("/..*")
    }
}
