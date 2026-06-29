plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "YuriGarden"
    versionCode = 9
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl = "https://yurigarden.moe"
    }
}

dependencies {

    implementation(project(":lib:cryptoaes"))
}
