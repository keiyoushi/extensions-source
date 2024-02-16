plugins {
    id("lib-multisrc")
}

baseVersionCode = 33

dependencies {
    api(project(":lib:cryptoaes"))
    api(project(":lib:randomua"))
}
