import keiyoushi.gradle.extensions.baseVersionCode

plugins {
    alias(kei.plugins.multisrc)
}

baseVersionCode = 2

dependencies {
    implementation(project(":lib:unpacker"))
}
