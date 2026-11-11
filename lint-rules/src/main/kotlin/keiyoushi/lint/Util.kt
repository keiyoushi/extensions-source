package keiyoushi.lint

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.getParentOfType

val KEIYOUSHI: Category = Category.create("Keiyoushi", 75)

const val HTTP_SOURCE = "eu.kanade.tachiyomi.source.online.HttpSource"
const val KEI_SOURCE = "keiyoushi.source.KeiSource"
const val SMANGA = "eu.kanade.tachiyomi.source.model.SManga"
const val SCHAPTER = "eu.kanade.tachiyomi.source.model.SChapter"
const val OKHTTP_INTERCEPTOR = "okhttp3.Interceptor"

inline fun <reified T : Detector> issue(
    id: String,
    brief: String,
    explanation: String,
    severity: Severity = Severity.WARNING,
    priority: Int = 5,
): Issue = Issue.create(
    id = id,
    briefDescription = brief,
    explanation = explanation.trimIndent(),
    category = KEIYOUSHI,
    priority = priority,
    severity = severity,
    implementation = Implementation(T::class.java, Scope.JAVA_FILE_SCOPE),
)

/** The call at the end of a receiver chain: `a.b().c()` → `c()`. */
fun UExpression?.asCall(): UCallExpression? = when (val e = this?.skipParens()) {
    is UCallExpression -> e
    is UQualifiedReferenceExpression -> e.selector as? UCallExpression
    else -> null
}

fun UExpression.skipParens(): UExpression {
    var e: UExpression = this
    while (e is UParenthesizedExpression) e = e.expression
    return e
}

fun UExpression?.stringValue(): String? = this?.skipParens()?.evaluateString()

fun PsiMethod.inClass(vararg names: String): Boolean = containingClass?.qualifiedName in names

fun JavaContext.isSubclass(cls: PsiClass?, name: String): Boolean = evaluator.extendsClass(cls, name, false)

fun JavaContext.inSourceClass(element: UElement): Boolean = isSubclass(element.getParentOfType<UClass>(true)?.javaPsi, HTTP_SOURCE)

fun JavaContext.inInterceptor(element: UElement): Boolean {
    var cls = element.getParentOfType<UClass>(true)
    while (cls != null) {
        if (evaluator.implementsInterface(cls.javaPsi, OKHTTP_INTERCEPTOR, false)) return true
        cls = cls.getParentOfType<UClass>(true)
    }
    if (element.getParentOfType<UMethod>(true)?.name == "intercept") return true
    // `addInterceptor { chain -> ... }`
    var lambda = element.getParentOfType<ULambdaExpression>(true)
    while (lambda != null) {
        val call = lambda.uastParent as? UCallExpression
        if (call?.methodName in INTERCEPTOR_ADDERS) return true
        lambda = lambda.getParentOfType<ULambdaExpression>(true)
    }
    return false
}

private val INTERCEPTOR_ADDERS = setOf("addInterceptor", "addNetworkInterceptor")

fun UElement.enclosingMethodName(): String? = getParentOfType<UMethod>(true)?.name

/** The parent call of an argument, looking through Kotlin's string template wrapper. */
fun UElement.argumentOf(): UCallExpression? {
    var parent = uastParent
    while (parent is UPolyadicExpression || parent is UParenthesizedExpression) parent = parent.uastParent
    return parent as? UCallExpression
}

/** The argument passed for [parameter], whatever order named arguments were written in. */
fun JavaContext.argument(node: UCallExpression, method: PsiMethod, parameter: String): UExpression? = evaluator.computeArgumentMapping(node, method).entries.firstOrNull { it.value.name == parameter }?.key

/** For a call, just the call (name and arguments) rather than the whole receiver chain. */
fun JavaContext.loc(node: UElement): Location = if (node is UCallExpression) getCallLocation(node, includeReceiver = false, includeArguments = true) else getLocation(node)
