import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "DaoMeoDen"
    versionCode = 3
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl {
            custom("https://daomeoden.net")
        }
    }

    deeplink {
        path("/truyen-tranh/..*")
        path("/doc-truyen-tranh/..*")
    }
}
