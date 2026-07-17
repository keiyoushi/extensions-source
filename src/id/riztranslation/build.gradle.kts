import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Riztranslation"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

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
