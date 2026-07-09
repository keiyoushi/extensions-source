plugins {
    alias(kei.plugins.multisrc)
}

dependencies {
    //noinspection UseTomlInstead
    implementation("org.brotli:dec:0.1.2")
}

keiyoushi {
    baseVersionCode = 35
    libVersion = "1.4"
}
