plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManHuaGui"
    className = "Manhuagui"
    versionCode = 28
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:lzstring"))
    implementation(project(":lib:unpacker"))
}
