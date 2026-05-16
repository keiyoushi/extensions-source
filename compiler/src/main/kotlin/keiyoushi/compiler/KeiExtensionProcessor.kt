package keiyoushi.compiler

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ClassName
import kotlinx.serialization.json.Json
import java.util.Base64

class KeiExtensionProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>,
) : SymbolProcessor {

    private var generated = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        // KSP can invoke us across multiple rounds in the same invocation; the
        // CodeGenerator rejects a second createNewFile for the same path.
        if (generated) return emptyList()

        val spec = decodeSpec()
        val annotated = resolver.getSymbolsWithAnnotation(EXTENSION_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.validate() }
            .toList()

        val target = pickTarget(annotated)

        validateClass(target)
        ensureExtendsSource(resolver, target)

        val configurableType = resolver.getClassDeclarationByName(resolver.getKSNameFromString(CONFIGURABLE_SOURCE_FQN))
            ?: error("Unable to resolve $CONFIGURABLE_SOURCE_FQN — is :core on the classpath?")
        val superIsConfigurable = configurableType.asStarProjectedType().isAssignableFrom(target.asStarProjectedType())
        val superOverridesSetupPrefs = if (superIsConfigurable) findConcreteSetupPrefs(target) else false

        if (superIsConfigurable && !superOverridesSetupPrefs) {
            error(
                "${target.qualifiedName?.asString()} declares ConfigurableSource as a supertype but does not " +
                    "provide a concrete `setupPreferenceScreen(screen)` override. The generated KeiExtension " +
                    "needs the super class to provide an implementation so it can call super.setupPreferenceScreen(screen).",
            )
        }

        val userClass = ClassName(target.packageName.asString(), target.simpleName.asString())
        val file = buildKeiExtensionFile(
            spec = spec,
            userClass = userClass,
            superIsConfigurable = superIsConfigurable,
            superOverridesSetupPrefs = superOverridesSetupPrefs,
        )

        codeGenerator.createNewFile(
            dependencies = Dependencies(false, target.containingFile!!),
            packageName = spec.pkg,
            fileName = GENERATED_NAME,
            extensionName = "kt",
        ).bufferedWriter().use { file.writeTo(it) }

        generated = true
        return emptyList()
    }

    private fun decodeSpec(): ExtensionSpec {
        val raw = options[SPEC_OPTION]
            ?: error("Missing KSP option `$SPEC_OPTION` — kei.plugins.extension did not pass the resolved spec.")
        val json = String(Base64.getDecoder().decode(raw))
        return Json.decodeFromString(ExtensionSpec.serializer(), json)
    }

    private fun pickTarget(candidates: List<KSClassDeclaration>): KSClassDeclaration = when (candidates.size) {
        0 -> error(
            "No class annotated with @keiyoushi.annotations.Extension was found in this extension's source set. " +
                "Add @Extension to the abstract class you want the generated KeiExtension to extend.",
        )
        1 -> candidates.single()
        else -> error(
            "Found ${candidates.size} classes annotated with @Extension " +
                "(${candidates.mapNotNull { it.qualifiedName?.asString() }.joinToString()}). " +
                "Exactly one class per extension may be annotated.",
        )
    }

    private fun validateClass(cls: KSClassDeclaration) {
        if (cls.classKind != ClassKind.CLASS) {
            error("@Extension can only be applied to a class (got ${cls.classKind} on ${cls.qualifiedName?.asString()}).")
        }
        if (Modifier.ABSTRACT !in cls.modifiers && Modifier.OPEN !in cls.modifiers) {
            error("${cls.qualifiedName?.asString()} must be `abstract` or `open` — KeiExtension extends it.")
        }
    }

    private fun ensureExtendsSource(resolver: Resolver, cls: KSClassDeclaration) {
        val sourceType = resolver.getClassDeclarationByName(resolver.getKSNameFromString(SOURCE_FQN))?.asStarProjectedType()
            ?: error("Unable to resolve $SOURCE_FQN.")
        if (!sourceType.isAssignableFrom(cls.asStarProjectedType())) {
            error("${cls.qualifiedName?.asString()} must extend HttpSource (or another Source subtype).")
        }
    }

    // Walks the supertype chain looking for a non-abstract setupPreferenceScreen(PreferenceScreen).
    private fun findConcreteSetupPrefs(cls: KSClassDeclaration): Boolean {
        return cls.getAllFunctions().any { fn ->
            fn.simpleName.asString() == "setupPreferenceScreen" &&
                Modifier.ABSTRACT !in fn.modifiers &&
                fn.parameters.size == 1 &&
                fn.parameters[0].type.resolve().declaration.qualifiedName?.asString() == PREFERENCE_SCREEN_FQN
        }
    }

    private fun error(message: String): Nothing {
        logger.error(message)
        throw IllegalStateException(message)
    }

    private companion object {
        const val SPEC_OPTION = "keiyoushi.spec"
        const val EXTENSION_ANNOTATION = "keiyoushi.annotations.Extension"
        const val SOURCE_FQN = "eu.kanade.tachiyomi.source.Source"
        const val CONFIGURABLE_SOURCE_FQN = "eu.kanade.tachiyomi.source.ConfigurableSource"
        const val PREFERENCE_SCREEN_FQN = "androidx.preference.PreferenceScreen"
    }
}

class KeiExtensionProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        KeiExtensionProcessor(environment.codeGenerator, environment.logger, environment.options)
}
