import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "The Draw Play"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        baseUrl = "https://www.thedrawplay.com"
        lang = "en"
    }

    deeplink {
        host("www.thedrawplay.com")
        host("thedrawplay.com")
        path("/comic/..*")
    }
}

dependencies {
    implementation(project(":lib:textinterceptor"))
}
