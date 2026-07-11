plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mehgazone"
    versionCode = 2
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://mehgazone.com"
    }
}

dependencies {

    implementation(project(":lib:textinterceptor"))
}
