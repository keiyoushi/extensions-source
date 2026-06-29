plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaNova"
    className = "MangaNova"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    deeplink {
        host("www.manga-nova.com")
        path("/lecture-en-ligne/..*")
        path("/manga/..*")
    }
}
