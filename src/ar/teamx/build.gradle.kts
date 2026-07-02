plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Team X"
    versionCode = 30
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "ar"
        baseUrl("https://olympustaff.com") {
            withCustom.set(true)
        }
    }

    deeplink {
        path("/series/..*")
    }
}
