plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Nekopost"
    versionCode = 15
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "th"
        baseUrl = "https://www.nekopost.net"
    }
}

dependencies {

    implementation(project(":lib:cryptoaes"))
}
