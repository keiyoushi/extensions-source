plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Toshokan Z"
    className = "MangaToshokanZ"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:cryptoaes"))
}
