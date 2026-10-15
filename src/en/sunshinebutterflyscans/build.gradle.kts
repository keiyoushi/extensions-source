import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Sunshine Butterfly Scans"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://wings.sbs"
        versionId = 2
    }

    deeplink {
        path("/projects")
    }
}

dependencies {

    implementation(project(":lib:cryptoaes"))
}
