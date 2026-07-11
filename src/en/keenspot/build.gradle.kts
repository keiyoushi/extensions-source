import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "keenspot"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        name = "Keenspot TwoKinds"
        lang = "en"
        baseUrl = "https://twokinds.keenspot.com"
        id = 3133607736276627986L
    }
}
