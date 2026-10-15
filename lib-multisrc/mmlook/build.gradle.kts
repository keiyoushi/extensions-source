plugins {
    alias(kei.plugins.multisrc)
}

dependencies {
    implementation(project(":lib:unpacker"))
}

keiyoushi {
    baseVersionCode = 0
    libVersion = "1.6"
}
