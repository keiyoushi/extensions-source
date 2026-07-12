import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Nekopost"
    versionCode = 15
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "th"
        baseUrl = "https://www.nekopost.net"
    }
}

dependencies {

    implementation(project(":lib:cryptoaes"))
}
