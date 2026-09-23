import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Dynasty Scans"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://dynasty-scans.com"
        id = 669095474988166464L
    }

    deeplink {
        host("dynasty-scans.com")
        host("*.dynasty-scans.com")
        path("/anthologies/..*")
        path("/chapters/..*")
        path("/doujins/..*")
        path("/issues/..*")
        path("/series/..*")
    }
}
