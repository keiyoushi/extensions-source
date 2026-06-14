
plugins {
    alias(kei.plugins.multisrc)
}

multisrc {
    baseVersionCode = 2
}

dependencies {
    implementation(project(":lib:unpacker"))
}
