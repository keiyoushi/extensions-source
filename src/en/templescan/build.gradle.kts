plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Temple Scan"
    versionCode = 50
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://templetoons.com"
        versionId = 3
    }
}

dependencies {

    implementation(project(":lib:randomua"))
}
