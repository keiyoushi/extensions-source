import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Otakusic"
    versionCode = 3
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl {
            custom("https://otakusic.com")
        }
    }

    deeplink {
        path("/chi-tiet/..*")
        path("/doc-truyen/..*")
    }
}
