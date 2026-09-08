import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MeTruyen18"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "madara"

    source {
        lang = "vi"
        baseUrl {
            custom("https://metruyen18.vip")
        }
    }

    deeplink {
        path("/truyen/.*")
    }
}
