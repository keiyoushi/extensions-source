import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Team X"
    versionCode = 33
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
