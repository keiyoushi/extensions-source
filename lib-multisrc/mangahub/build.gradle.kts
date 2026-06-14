
plugins {
    alias(kei.plugins.multisrc)
}

multisrc {
    baseVersionCode = 35
}

dependencies {
    //noinspection UseTomlInstead
    implementation("org.brotli:dec:0.1.2")
}
