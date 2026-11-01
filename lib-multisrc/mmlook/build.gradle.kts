plugins {
    alias(kei.plugins.multisrc)
}

dependencies {
    implementation(project(":lib:unpacker"))
}

keiyoushi {
    baseVersionCode = 1
    libVersion = "1.6"
}
