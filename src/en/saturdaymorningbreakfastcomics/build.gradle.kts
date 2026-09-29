import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Saturday Morning Breakfast Comics"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://smbc-comics.com"
    }
}

dependencies {

    implementation(project(":lib:textinterceptor"))
}
