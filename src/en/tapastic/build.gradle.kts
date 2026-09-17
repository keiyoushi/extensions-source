import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Tapas"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://tapas.io"
        versionId = 2
    }

    deeplink {
        path("/series/..*")
    }
}

dependencies {

    implementation(project(":lib:textinterceptor"))
}
