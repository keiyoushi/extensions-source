plugins {
    id("lib-multisrc")
}

baseVersionCode = 9

dependencies {
    api(project(":lib:synchrony"))
    implementation(project(":lib:dataimage"))
    implementation(project(":lib:randomua"))
}
