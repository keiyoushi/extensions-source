import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "CuuTruyen"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl {
            mirrors(
                "https://cuutruyen.net",
                "https://hetcuutruyen.net",
            )
        }
    }

    deeplink {
        path("/mangas/.*")
    }
}
