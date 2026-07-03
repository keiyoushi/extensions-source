plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Team X"
    versionCode = 30
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        baseUrl("https://olympustaff.com") {
            withCustom = true
        }
        lang = "ar"
    }

    deeplink {
        path("/series/..*")
    }
}
