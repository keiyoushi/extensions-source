plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Toshokan Z"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        name = "マンガ図書館Z"
        lang = "ja"
        baseUrl = "https://www.mangaz.com"
    }
}

dependencies {

    implementation(project(":lib:cryptoaes"))
}
