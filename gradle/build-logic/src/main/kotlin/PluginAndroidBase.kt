import com.android.build.api.dsl.ApplicationDefaultConfig
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.CompileOptions
import com.android.build.api.dsl.DefaultConfig
import com.android.build.api.dsl.LibraryDefaultConfig
import keiyoushi.gradle.configurations.configureKotlin
import keiyoushi.gradle.extensions.kei
import keiyoushi.gradle.extensions.libs
import keiyoushi.gradle.extensions.spotlessTaskName
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

@Suppress("UNUSED")
class PluginAndroidBase : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        configureKotlin()

        android {
            compileSdk = kei.versions.android.sdk.compile.get().toInt()

            defaultConfig {
                minSdk = kei.versions.android.sdk.min.get().toInt()
                if (this is ApplicationDefaultConfig) {
                    targetSdk = kei.versions.android.sdk.target.get().toInt()
                }

                val proguardFile = file("proguard-rules.pro")
                if (proguardFile.exists()) {
                    when (this) {
                        is ApplicationDefaultConfig -> proguardFile(proguardFile)
                        is LibraryDefaultConfig -> consumerProguardFiles(proguardFile)
                    }
                }
            }
        }

        tasks.getByName("preBuild").dependsOn(spotlessTaskName())
    }
}

private fun Project.android(block: CommonExtension.() -> Unit) {
    extensions.configure(block)
}

private fun CommonExtension.defaultConfig(block: DefaultConfig.() -> Unit) {
    defaultConfig.apply(block)
}
