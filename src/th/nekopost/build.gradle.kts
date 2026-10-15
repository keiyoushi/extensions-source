import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Nekopost"
    versionCode = 16
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "th"
        baseUrl = "https://www.nekopost.net"
    }

    deeplink {
        path("/manga/..*")
        path("/editor/..*")
    }
}

dependencies {

    implementation(project(":lib:cryptoaes"))
}
