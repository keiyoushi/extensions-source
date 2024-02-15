plugins {
    id("lib-multisrc")
}

baseVersionCode = 34

dependencies {
    api(project(":lib:cryptoaes"))
}
