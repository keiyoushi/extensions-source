plugins {
    id("lib-multisrc")
}

baseVersionCode = 2

dependencies {
    implementation(project(":lib:unpacker"))
}
