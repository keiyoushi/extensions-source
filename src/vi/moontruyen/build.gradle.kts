plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MoonTruyen"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl = "https://moontruyen.com"
    }
}

dependencies {

    implementation(project(":lib:cryptoaes"))
}
