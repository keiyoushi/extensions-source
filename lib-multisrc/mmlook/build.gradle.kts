plugins {
    id("lib-multisrc")
}

baseVersionCode = 1

dependencies {
    implementation(project(":lib:unpacker"))
}
