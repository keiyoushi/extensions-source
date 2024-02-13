plugins {
    id("lib-multisrc")
}

baseVersionCode = 33

dependencies {
    implementation(project(":lib:cryptoaes"))
    implementation(project(":lib:randomua"))
}
