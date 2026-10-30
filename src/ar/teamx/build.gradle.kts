import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Team X"
    versionCode = 34
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "ar"
        baseUrl {
            custom("https://www.olympustaff.com")
        }
    }

    deeplink {
        path("/series/..*")
    }
}
