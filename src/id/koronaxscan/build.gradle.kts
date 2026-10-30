import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "KoronaxScan"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "id"
        baseUrl = "https://project.koronaxscan.my.id"
    }

    deeplink {
        path("/detail/..*")
        path("/episode/..*")
        path("/reader/..*")
    }
}
