import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Tapas"
    versionCode = 24
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://tapas.io"
        versionId = 2
    }
}

dependencies {

    implementation(project(":lib:textinterceptor"))
}
