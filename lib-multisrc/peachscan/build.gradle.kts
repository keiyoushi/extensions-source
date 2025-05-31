plugins {
    id("lib-multisrc")
}

baseVersionCode = 9

dependencies {
    implementation(project(":lib:zipinterceptor"))
}
