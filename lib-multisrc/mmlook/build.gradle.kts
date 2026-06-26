plugins {
    alias(kei.plugins.multisrc)
}

dependencies {
    implementation(project(":lib:unpacker"))
}

keiyoushi {
    baseVersionCode = 2
    libVersion = "1.4"
}
