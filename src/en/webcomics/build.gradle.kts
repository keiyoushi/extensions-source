import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Webcomics"
    versionCode = 11
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://webcomicsapp.com"
    }

    deeplink {
        path("/comic/..*")
    }
}
