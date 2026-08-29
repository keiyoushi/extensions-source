import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "LoppyToon"
    versionCode = 6
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl {
            custom("https://loppytoon.com")
        }
    }

    deeplink {
        path("/truyen/..*")
    }
}
