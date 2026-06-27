plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Scantrad Union"
    className = "ScantradUnion"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    deeplink {
        host("scantrad-union.com")
        path("/manga/..*")
        path("/projets/..*")
        path("/read/..*")
    }
}
