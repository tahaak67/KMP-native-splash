package ly.com.tahaben.kmpnativesplash.android

import ly.com.tahaben.kmpnativesplash.util.childElements
import ly.com.tahaben.kmpnativesplash.util.parseXml
import ly.com.tahaben.kmpnativesplash.util.writeXml
import org.w3c.dom.Element
import java.io.File

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

internal data class ManifestPatchReport(
    val manifestPath: File,
    val foundLauncherActivity: Boolean,
    val foundAnyActivity: Boolean,
    val orientationSet: String? = null,
    val orientationRemoved: Boolean = false,
    val themeApplied: String? = null,
    val themeReplaced: String? = null,
    val themeUnchanged: Boolean = false,
)

/**
 * Patches `AndroidManifest.xml` in-place: optionally sets `android:screenOrientation`
 * and `android:theme` on the launcher activity (or the first `<activity>` if no
 * `MAIN/LAUNCHER` intent-filter is present).
 *
 * Returns a report describing what was changed so callers can log clearly.
 */
internal fun patchManifest(
    manifest: File,
    screenOrientation: String?,
    applyTheme: Boolean,
    themeName: String,
): ManifestPatchReport? {
    if (!manifest.exists()) return null
    val doc = parseXml(manifest)
    val root = doc.documentElement ?: return null
    val app = root.childElements().firstOrNull { it.tagName == "application" } ?: return null

    val launcher = findLauncherActivity(app)
    val anyActivity = launcher ?: app.childElements().firstOrNull { it.tagName == "activity" }
    if (anyActivity == null) {
        return ManifestPatchReport(manifestPath = manifest, foundLauncherActivity = false, foundAnyActivity = false)
    }
    val target: Element = launcher ?: anyActivity

    var changed = false

    // ---- screenOrientation ----
    val oldOrientation = target.getAttributeNS(ANDROID_NS, "screenOrientation").ifEmpty { null }
    var orientationSet: String? = null
    var orientationRemoved = false
    if (screenOrientation != null) {
        if (oldOrientation != screenOrientation) {
            target.setAttributeNS(ANDROID_NS, "android:screenOrientation", screenOrientation)
            changed = true
            orientationSet = screenOrientation
        }
    } else if (oldOrientation != null) {
        target.removeAttributeNS(ANDROID_NS, "screenOrientation")
        changed = true
        orientationRemoved = true
    }

    // ---- theme ----
    var themeApplied: String? = null
    var themeReplaced: String? = null
    var themeUnchanged = false
    if (applyTheme) {
        val oldTheme = target.getAttributeNS(ANDROID_NS, "theme").ifEmpty { null }
        if (oldTheme == themeName) {
            themeUnchanged = true
        } else {
            target.setAttributeNS(ANDROID_NS, "android:theme", themeName)
            changed = true
            themeApplied = themeName
            themeReplaced = oldTheme
        }
    }

    if (changed) writeXml(doc, manifest)

    return ManifestPatchReport(
        manifestPath = manifest,
        foundLauncherActivity = launcher != null,
        foundAnyActivity = true,
        orientationSet = orientationSet,
        orientationRemoved = orientationRemoved,
        themeApplied = themeApplied,
        themeReplaced = themeReplaced,
        themeUnchanged = themeUnchanged,
    )
}

/** Find the `<activity>` whose `<intent-filter>` carries both MAIN action and LAUNCHER category. */
private fun findLauncherActivity(app: Element): Element? = app.childElements()
    .filter { it.tagName == "activity" }
    .firstOrNull { activity ->
        activity.childElements()
            .filter { it.tagName == "intent-filter" }
            .any { filter ->
                val children = filter.childElements()
                val hasMain = children.any {
                    it.tagName == "action" &&
                            it.getAttributeNS(ANDROID_NS, "name") == "android.intent.action.MAIN"
                }
                val hasLauncher = children.any {
                    it.tagName == "category" &&
                            it.getAttributeNS(ANDROID_NS, "name") == "android.intent.category.LAUNCHER"
                }
                hasMain && hasLauncher
            }
    }
