package io.github.keiyoushi.gradle.tasks

import com.android.apksig.ApkSigner
import com.android.apksig.KeyConfig
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

@DisableCachingByDefault(because = "Signing is cheap and the keystore isn't a tracked input")
abstract class SignExtensionJarTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputJar: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val keystore: ConfigurableFileCollection

    @get:Input
    abstract val keyAlias: Property<String>

    @get:Input
    abstract val minSdkVersion: Property<Int>

    @get:Internal
    abstract val storePassword: Property<String>

    @get:Internal
    abstract val keyPassword: Property<String>

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @TaskAction
    fun sign() {
        val input = inputJar.get().asFile
        val out = outputJar.get().asFile
        out.parentFile.mkdirs()

        val ks = keystore.files.firstOrNull()?.takeIf { it.exists() }
        if (ks == null) {
            logger.lifecycle("No keystore available — writing unsigned jar: ${out.name}")
            input.copyTo(out, overwrite = true)
            return
        }

        val keyStore = loadKeyStore(ks, storePassword.get().toCharArray())
        val alias = keyAlias.get()
        val key = keyStore.getKey(alias, keyPassword.get().toCharArray()) as? PrivateKey
            ?: error("Keystore ${ks.name} has no private key for alias '$alias'")
        val certs = (keyStore.getCertificateChain(alias) ?: error("Alias '$alias' has no certificate chain"))
            .map { it as X509Certificate }

        val signerConfig = ApkSigner.SignerConfig.Builder("CERT", KeyConfig.Jca(key), certs, true).build()
        ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(input)
            .setOutputApk(out)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(false)
            .setV3SigningEnabled(false)
            .setMinSdkVersion(minSdkVersion.get())
            .build()
            .sign()
    }

    private fun loadKeyStore(file: File, password: CharArray): KeyStore {
        for (type in listOf("JKS", "PKCS12")) {
            try {
                return KeyStore.getInstance(type).apply { file.inputStream().use { load(it, password) } }
            } catch (_: Exception) {
                // try the next store type
            }
        }
        error("Unable to load keystore ${file.name} as JKS or PKCS12")
    }
}
