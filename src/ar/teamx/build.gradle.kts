plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Team X"
    versionCode = 31
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "ar"
        baseUrl {
            custom("https://olympustaff.com")
        }
    }

    deeplink {
        path("/series/..*")
    }
}
