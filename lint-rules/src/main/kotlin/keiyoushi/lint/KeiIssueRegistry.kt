package keiyoushi.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue
import keiyoushi.lint.detectors.BuildScriptDetector
import keiyoushi.lint.detectors.DateTimeDetector
import keiyoushi.lint.detectors.IconDetector
import keiyoushi.lint.detectors.JsonDetector
import keiyoushi.lint.detectors.JsoupDetector
import keiyoushi.lint.detectors.KotlinIdiomDetector
import keiyoushi.lint.detectors.NetworkDetector
import keiyoushi.lint.detectors.SourceDetector

class KeiIssueRegistry : IssueRegistry() {
    override val issues: List<Issue> =
        NetworkDetector.ISSUES +
            JsonDetector.ISSUES +
            JsoupDetector.ISSUES +
            DateTimeDetector.ISSUES +
            KotlinIdiomDetector.ISSUES +
            SourceDetector.ISSUES +
            BuildScriptDetector.ISSUES +
            IconDetector.ISSUES

    override val api: Int = CURRENT_API

    override val minApi: Int = 14

    override val vendor: Vendor = Vendor(
        vendorName = "Keiyoushi",
        identifier = "keiyoushi-lint-rules",
        feedbackUrl = "https://github.com/keiyoushi/extensions-source/issues",
    )
}
