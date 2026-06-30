plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Monochrome Scans"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "monochrome"

    deeplink {
        host("manga.d34d.one")
        host("*.manga.d34d.one")
        path("/manga/..*")
    }

    source {
        lang = "en"
        baseUrl = "https://manga.d34d.one"
    }
}
