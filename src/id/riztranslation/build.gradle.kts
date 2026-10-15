import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Riztranslation"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "id"
        baseUrl = "https://riztranslation.pages.dev"
    }

    deeplink {
        host("riztranslation.pages.dev")
        host("riztranslation.rf.gd")
        path("/..*")
    }
}
