package ly.com.tahaben.kmpnativesplash.ios

import ly.com.tahaben.kmpnativesplash.util.childElements
import ly.com.tahaben.kmpnativesplash.util.findChild
import ly.com.tahaben.kmpnativesplash.util.parseXml
import ly.com.tahaben.kmpnativesplash.util.writeXml
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File

/**
 * Idempotently sets `UIStatusBarHidden` and `UIViewControllerBasedStatusBarAppearance`
 * on the plist's root `<dict>`.
 */
internal fun patchInfoPlist(file: File, fullscreen: Boolean) {
    if (!file.exists()) return
    val doc = parseXml(file, allowDoctype = true)
    val root = doc.documentElement ?: return
    val dict = root.findChild("dict") ?: return

    setBoolKey(doc, dict, "UIStatusBarHidden", fullscreen)
    if (fullscreen) {
        setBoolKey(doc, dict, "UIViewControllerBasedStatusBarAppearance", false)
    } else {
        removeKey(dict, "UIViewControllerBasedStatusBarAppearance")
    }
    writeXml(doc, file, omitDeclaration = false, plist = true)
}

private fun setBoolKey(doc: Document, dict: Element, key: String, value: Boolean) {
    val (existingKey, existingValue) = findKeyAndValue(dict, key)
    val valueTag = if (value) "true" else "false"
    if (existingKey != null && existingValue != null) {
        // Replace value node.
        val replacement = doc.createElement(valueTag)
        dict.replaceChild(replacement, existingValue)
    } else {
        val keyEl = doc.createElement("key").apply { textContent = key }
        val valEl = doc.createElement(valueTag)
        dict.appendChild(keyEl)
        dict.appendChild(valEl)
    }
}

private fun removeKey(dict: Element, key: String) {
    val (existingKey, existingValue) = findKeyAndValue(dict, key)
    if (existingKey != null) dict.removeChild(existingKey)
    if (existingValue != null) dict.removeChild(existingValue)
}

private fun findKeyAndValue(dict: Element, key: String): Pair<Element?, Element?> {
    val children = dict.childElements()
    val keyIndex = children.indexOfFirst { it.tagName == "key" && it.textContent.trim() == key }
    if (keyIndex < 0) return null to null
    val keyEl = children[keyIndex]
    val valEl = children.getOrNull(keyIndex + 1)
    return keyEl to valEl
}

/**
 * Sets `UILaunchStoryboardName` to [value] (typically `$(LAUNCH_SCREEN_STORYBOARD)` so the
 * per-config build setting routes the right storyboard). Idempotent. Returns `true` if the
 * file existed and was processed; `false` if there is no on-disk Info.plist (typical when
 * the project uses `GENERATE_INFOPLIST_FILE = YES`).
 */
internal fun setUILaunchStoryboardName(file: File, value: String): Boolean {
    if (!file.exists()) return false
    val doc = parseXml(file, allowDoctype = true)
    val root = doc.documentElement ?: return false
    val dict = root.findChild("dict") ?: return false

    val (existingKey, existingValue) = findKeyAndValue(dict, "UILaunchStoryboardName")
    if (existingKey != null && existingValue != null) {
        existingValue.textContent = value
        // If the existing element isn't <string>, replace it.
        if (existingValue.tagName != "string") {
            val replacement = doc.createElement("string").apply { textContent = value }
            dict.replaceChild(replacement, existingValue)
        }
    } else {
        val keyEl = doc.createElement("key").apply { textContent = "UILaunchStoryboardName" }
        val valEl = doc.createElement("string").apply { textContent = value }
        dict.appendChild(keyEl)
        dict.appendChild(valEl)
    }
    writeXml(doc, file, omitDeclaration = false, plist = true)
    return true
}
