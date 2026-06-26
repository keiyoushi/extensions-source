plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Yugen Mangás"
    className = "YugenMangas"
    versionCode = 51
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:randomua"))
}
