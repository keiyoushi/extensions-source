import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ProComic"
    versionCode = 8
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        baseUrl = "https://procomic.net"
        lang = "ar"
        versionId = 2
    }

    deeplink {
        host("procomic.pro")
        host("procomic.net")
        path("/..*-..*")
    }
}

dependencies {
    implementation(project(":lib:cronet"))
    implementation(project(":lib:imagedecoder"))
}
