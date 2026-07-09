plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Taddy INK (Webtoons)"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "all"
        baseUrl = "https://taddy.org"
    }

    deeplink {
        host("taddy.org")
        path("/..*/..*")
    }
}
