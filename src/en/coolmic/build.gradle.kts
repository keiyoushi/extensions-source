import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Coolmic"
    versionCode = 3
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://coolmic.me"
    }
}

dependencies {

    implementation(project(":lib:cookieinterceptor"))
}
