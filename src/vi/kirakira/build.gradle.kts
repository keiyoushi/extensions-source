import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "KiraKira"
    versionCode = 4
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl {
            custom("https://truyenkira.net")
        }
    }

    deeplink {
        path("/comics/..*")
        path("/chapters/..*")
    }
}
