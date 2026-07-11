plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Sunshine Butterfly Scans"
    versionCode = 39
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://wings.sbs"
        versionId = 2
    }
}

dependencies {

    implementation(project(":lib:cryptoaes"))
}
