import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MeoSSS"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl {
            custom("https://meosss.com")
        }
    }

    deeplink {
        path("/truyen/..*")
    }
}
