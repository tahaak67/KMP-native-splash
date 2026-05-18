package ly.com.tahaben.kmpnativesplash.ios

import java.io.File
import java.security.MessageDigest

/**
 * Editor for the OpenStep-style `project.pbxproj` files produced by Xcode. Performs three
 * idempotent edits (skips work if already in place):
 *
 *  1. **PBXFileReference + PBXBuildFile + Resources phase + Base.lproj group** — registers
 *     each generated storyboard with the project so Xcode actually copies it into the bundle.
 *  2. **`LAUNCH_SCREEN_STORYBOARD` build setting** — added under each XCBuildConfiguration's
 *     `buildSettings`, mapped from configuration name suffix (e.g. `Debug-dev` → `LaunchScreenDev`).
 *  3. (Caller-driven) Info.plist edit to switch `UILaunchStoryboardName` to
 *     `$(LAUNCH_SCREEN_STORYBOARD)`. See [InfoPlistPatcher].
 *
 * Object IDs are derived from `sha1("kmpnativesplash:<kind>:<storyboard>")` so re-runs reuse the
 * same 24-char hex ID rather than producing diffs every build. **The format is hand-edited,
 * not parsed via a proper PBX grammar, so this is best-effort and explicitly opt-in.**
 */
internal data class PatchPlan(
    val storyboardNamesByConfiguration: Map<String, String>,
    /** All storyboard file names to register with the project (without `.storyboard`). */
    val allStoryboards: Set<String>,
    /**
     * Forward-slash relative path from the `.xcodeproj`'s parent directory (Xcode's
     * `SOURCE_ROOT`) to the directory that actually contains the generated storyboards on
     * disk — typically `<iosAppDirName>/Base.lproj`. Used as the PBXFileReference `path` for
     * newly registered storyboards, with `sourceTree = SOURCE_ROOT`, so resolution is
     * independent of which Xcode group the file ref ends up in.
     */
    val storyboardDirRelativeToSourceRoot: String,
)

internal fun patchPbxproj(pbxprojFile: File, plan: PatchPlan): PbxPatchReport {
    require(pbxprojFile.exists()) { "pbxproj not found: ${pbxprojFile.absolutePath}" }
    var content = pbxprojFile.readText(Charsets.UTF_8)
    val report = PbxPatchReport()

    plan.allStoryboards.forEach { name ->
        content = ensureStoryboardRegistered(content, name, plan.storyboardDirRelativeToSourceRoot, report)
    }
    content = ensureLaunchScreenBuildSettings(content, plan.storyboardNamesByConfiguration, report)

    pbxprojFile.writeText(content, Charsets.UTF_8)
    return report
}

internal data class PbxPatchReport(
    val addedReferences: MutableList<String> = mutableListOf(),
    val updatedConfigurations: MutableList<String> = mutableListOf(),
    /** Every XCBuildConfiguration name → the storyboard its `LAUNCH_SCREEN_STORYBOARD` now points at. */
    val configStoryboard: MutableMap<String, String> = linkedMapOf(),
    /** Configs where `INFOPLIST_KEY_UILaunchStoryboardName` was also routed through `$(LAUNCH_SCREEN_STORYBOARD)`. */
    val infoPlistKeyConfigurations: MutableList<String> = mutableListOf(),
    /** True if any config uses `GENERATE_INFOPLIST_FILE = YES` (Xcode then ignores on-disk Info.plist edits). */
    var usesGeneratedInfoPlist: Boolean = false,
)

private val GENERATE_INFOPLIST_REGEX = Regex("""GENERATE_INFOPLIST_FILE\s*=\s*YES""")
private val INFOPLIST_KEY_LAUNCH_REGEX = Regex("""INFOPLIST_KEY_UILaunchStoryboardName\s*=""")

// ----- File-tree registration -----

private fun ensureStoryboardRegistered(
    content: String,
    storyboardName: String,
    storyboardDirRelativeToSourceRoot: String,
    report: PbxPatchReport,
): String {
    val storyboardFile = "$storyboardName.storyboard"

    // The default `LaunchScreen.storyboard` shipped by Xcode templates is wrapped in a
    // PBXVariantGroup (`children = ( Base, ); name = LaunchScreen.storyboard;`). Treat that
    // as already-registered so we don't bolt a duplicate file ref onto it.
    if (findExistingVariantGroupId(content, storyboardFile) != null) return content

    val existingFileRefId = findExistingFileRefId(content, storyboardFile)
    val fileRefId = existingFileRefId ?: stableId("fileref:$storyboardFile")

    // If a BuildFile already references this fileRef, we're done — the project is already wired.
    if (existingFileRefId != null && findExistingBuildFileIdFor(content, existingFileRefId) != null) {
        return content
    }
    val buildFileId = stableId("buildfile:$storyboardFile")
    if (existingFileRefId != null) {
        report.addedReferences += "$storyboardFile (build-file only)"
        return addBuildFileAndResourcesEntry(content, fileRefId, buildFileId, storyboardFile)
    }
    if (content.contains(fileRefId)) return content // Idempotent: previously added by us.
    report.addedReferences += storyboardFile

    var result = content

    // 1. PBXFileReference entry. We resolve via `sourceTree = SOURCE_ROOT` + a path relative
    //    to the `.xcodeproj`'s parent dir so the file ref does not depend on which Xcode
    //    group it ends up in (the user's project may use a PBXVariantGroup instead of a
    //    PBXGroup for Base.lproj).
    val refPath = if (storyboardDirRelativeToSourceRoot.isEmpty()) storyboardFile
    else "$storyboardDirRelativeToSourceRoot/$storyboardFile"
    val fileRefLine =
        """		$fileRefId /* $storyboardFile */ = {isa = PBXFileReference; lastKnownFileType = file.storyboard; name = $storyboardFile; path = $refPath; sourceTree = SOURCE_ROOT; };"""
    result = insertAtSectionStart(result, "/* Begin PBXFileReference section */", fileRefLine) ?: result

    // 2. PBXBuildFile entry.
    val buildFileLine =
        """		$buildFileId /* $storyboardFile in Resources */ = {isa = PBXBuildFile; fileRef = $fileRefId /* $storyboardFile */; };"""
    result = insertAtSectionStart(result, "/* Begin PBXBuildFile section */", buildFileLine) ?: result

    // 3. Add into the first PBXResourcesBuildPhase's `files = ( ... );`.
    result = injectIntoResourcesBuildPhase(result, buildFileId, storyboardFile) ?: result

    // 4. Best-effort cosmetic: surface the file in the Xcode navigator by adding it to a
    //    group. Prefer the Base.lproj PBXGroup if one exists, otherwise fall back to the
    //    parent group of the existing `LaunchScreen.storyboard` variant group. Build
    //    correctness does not depend on this — `sourceTree = SOURCE_ROOT` above already
    //    resolves the path unconditionally.
    result = injectIntoBaseLprojGroup(result, fileRefId, storyboardFile)
        ?: injectIntoVariantGroupParent(result, fileRefId, storyboardFile)
                ?: result

    return result
}

private fun findExistingFileRefId(content: String, storyboardFile: String): String? {
    // Match a PBXFileReference whose path either is the storyboard file directly or ends with
    // `/<storyboardFile>` (covers `Base.lproj/LaunchScreen.storyboard`, `iosApp/Base.lproj/...`,
    // etc.). The comment is allowed to be either the storyboard file name itself or `Base`
    // (the latter appears as the child of a PBXVariantGroup in stock Xcode templates).
    val pattern = Regex(
        """(\w{24}) /\* (?:${Regex.escape(storyboardFile)}|Base) \*/ = \{isa = PBXFileReference;[^}]*?path = [^;]*?${
            Regex.escape(
                storyboardFile
            )
        };""",
    )
    return pattern.find(content)?.groups?.get(1)?.value
}

private fun findExistingVariantGroupId(content: String, storyboardFile: String): String? {
    val pattern = Regex(
        """(\w{24}) /\* ${Regex.escape(storyboardFile)} \*/ = \{\s*isa = PBXVariantGroup;""",
    )
    return pattern.find(content)?.groups?.get(1)?.value
}

private fun findExistingBuildFileIdFor(content: String, fileRefId: String): String? {
    val pattern = Regex("""(\w{24}) /\* [^*]+ in Resources \*/ = \{isa = PBXBuildFile; fileRef = $fileRefId """)
    return pattern.find(content)?.groups?.get(1)?.value
}

private fun addBuildFileAndResourcesEntry(
    content: String,
    fileRefId: String,
    buildFileId: String,
    storyboardFile: String
): String {
    val buildFileLine =
        """		$buildFileId /* $storyboardFile in Resources */ = {isa = PBXBuildFile; fileRef = $fileRefId /* $storyboardFile */; };"""
    var result = insertAtSectionStart(content, "/* Begin PBXBuildFile section */", buildFileLine) ?: content
    // Add to resources phase only if not already present.
    if (!Regex("""$buildFileId /\* ${Regex.escape(storyboardFile)} in Resources \*/,""").containsMatchIn(result)) {
        result = injectIntoResourcesBuildPhase(result, buildFileId, storyboardFile) ?: result
    }
    return result
}

private fun insertAtSectionStart(content: String, marker: String, line: String): String? {
    val idx = content.indexOf(marker)
    if (idx < 0) return null
    val afterMarker = idx + marker.length
    val newlineIdx = content.indexOf('\n', afterMarker).takeIf { it >= 0 } ?: return null
    return content.substring(0, newlineIdx + 1) + line + "\n" + content.substring(newlineIdx + 1)
}

private fun injectIntoResourcesBuildPhase(content: String, buildFileId: String, storyboardFile: String): String? {
    val phaseRegex = Regex(
        """isa = PBXResourcesBuildPhase;[\s\S]*?files = \(([\s\S]*?)\);""",
    )
    val match = phaseRegex.find(content) ?: return null
    val filesGroup = match.groups[1] ?: return null
    val newFilesContent =
        filesGroup.value.trimEnd() + "\n\t\t\t\t$buildFileId /* $storyboardFile in Resources */,\n\t\t\t"
    return content.substring(0, filesGroup.range.first) + newFilesContent + content.substring(filesGroup.range.last + 1)
}

private fun injectIntoBaseLprojGroup(content: String, fileRefId: String, storyboardFile: String): String? {
    val baseGroupRegex = Regex(
        """\w{24} /\* Base\.lproj \*/ = \{[\s\S]*?children = \(([\s\S]*?)\);[\s\S]*?path = Base\.lproj;""",
    )
    val match = baseGroupRegex.find(content) ?: return null
    val childrenGroup = match.groups[1] ?: return null
    val newChildren = childrenGroup.value.trimEnd() + "\n\t\t\t\t$fileRefId /* $storyboardFile */,\n\t\t\t"
    return content.substring(
        0,
        childrenGroup.range.first
    ) + newChildren + content.substring(childrenGroup.range.last + 1)
}

/**
 * Fallback for stock Xcode templates that store `LaunchScreen.storyboard` in a
 * PBXVariantGroup rather than a Base.lproj PBXGroup. We find the variant group's ID, then
 * the PBXGroup whose `children = ( … )` list contains that ID, and append the new file ref
 * to that parent group so the new storyboard shows up next to the existing one in the
 * navigator.
 */
private fun injectIntoVariantGroupParent(content: String, fileRefId: String, storyboardFile: String): String? {
    val variantGroupIdRegex = Regex(
        """(\w{24}) /\* LaunchScreen\.storyboard \*/ = \{\s*isa = PBXVariantGroup;""",
    )
    val variantGroupId = variantGroupIdRegex.find(content)?.groups?.get(1)?.value ?: return null
    val parentRegex = Regex(
        """\w{24}(?: /\*[^*]*\*/)? = \{\s*isa = PBXGroup;[\s\S]*?children = \(([\s\S]*?$variantGroupId[\s\S]*?)\);""",
    )
    val match = parentRegex.find(content) ?: return null
    val childrenGroup = match.groups[1] ?: return null
    val newChildren = childrenGroup.value.trimEnd() + "\n\t\t\t\t$fileRefId /* $storyboardFile */,\n\t\t\t"
    return content.substring(
        0,
        childrenGroup.range.first
    ) + newChildren + content.substring(childrenGroup.range.last + 1)
}

// ----- Build-settings patching -----

private val XC_CONFIG_REGEX = Regex(
    """(\w{24}) /\* ([^*]+?) \*/ = \{\s*isa = XCBuildConfiguration;[\s\S]*?buildSettings = \{([\s\S]*?)\};[\s\S]*?name = ([^;]+);\s*\};""",
)

private fun ensureLaunchScreenBuildSettings(
    content: String,
    storyboardByConfig: Map<String, String>,
    report: PbxPatchReport,
): String {
    if (storyboardByConfig.isEmpty()) return content
    val sb = StringBuilder()
    var lastEnd = 0
    XC_CONFIG_REGEX.findAll(content).forEach { match ->
        val nameRaw = match.groups[4]!!.value.trim().trim('"')
        val storyboard = lookupStoryboardForConfig(nameRaw, storyboardByConfig)
        if (storyboard == null) {
            sb.append(content, lastEnd, match.range.last + 1)
            lastEnd = match.range.last + 1
            return@forEach
        }
        val buildSettingsGroup = match.groups[3]!!
        val original = buildSettingsGroup.value
        var newBuildSettings = upsertSetting(original, "LAUNCH_SCREEN_STORYBOARD", storyboard)
        // Modern Xcode/KMP projects generate Info.plist from build settings
        // (GENERATE_INFOPLIST_FILE = YES) and/or inject UILaunchStoryboardName via the
        // INFOPLIST_KEY_UILaunchStoryboardName build setting. There, editing the on-disk
        // Info.plist is ignored by Xcode, so route that build-setting key through the same
        // $(LAUNCH_SCREEN_STORYBOARD) indirection too.
        val generatesPlist = GENERATE_INFOPLIST_REGEX.containsMatchIn(original)
        val hasInfoPlistKey = INFOPLIST_KEY_LAUNCH_REGEX.containsMatchIn(original)
        if (generatesPlist) report.usesGeneratedInfoPlist = true
        if (generatesPlist || hasInfoPlistKey) {
            newBuildSettings = upsertSetting(
                newBuildSettings, "INFOPLIST_KEY_UILaunchStoryboardName", "\$(LAUNCH_SCREEN_STORYBOARD)",
            )
            report.infoPlistKeyConfigurations += nameRaw
        }
        sb.append(content, lastEnd, buildSettingsGroup.range.first)
        sb.append(newBuildSettings)
        sb.append(content, buildSettingsGroup.range.last + 1, match.range.last + 1)
        lastEnd = match.range.last + 1
        report.updatedConfigurations += nameRaw
        report.configStoryboard[nameRaw] = storyboard
    }
    sb.append(content, lastEnd, content.length)
    return sb.toString()
}

internal fun lookupStoryboardForConfig(configName: String, storyboardByConfig: Map<String, String>): String? {
    storyboardByConfig[configName]?.let { return it }
    // Case-insensitive full-name match (handles `devDebug` vs seeded `DevDebug`).
    storyboardByConfig.entries.firstOrNull { it.key.equals(configName, ignoreCase = true) }?.let { return it.value }

    val defaultStoryboard = storyboardByConfig["default"] ?: storyboardByConfig["Debug"] ?: storyboardByConfig[""]
    // Tokenize on separators, camelCase, and letter↔digit boundaries so every common
    // convention splits cleanly: `Debug-dev`, `dev Debug`, `devDebug`, `prodRelease`,
    // `Release-prod`, `Staging2Debug` → the flavor token is always isolated.
    val tokens = configName.split(Regex("[-_ ]+|(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Za-z])(?=[0-9])"))
        .filter { it.isNotBlank() }

    // A token naming a *flavor* (its storyboard is non-default) wins over the build-type
    // token. Without this, `devDebug` → token "Debug" → seeded default `LaunchScreen`,
    // which is the bug that makes every `{flavor}Debug` config show the default at runtime.
    for (token in tokens) {
        storyboardByConfig.entries
            .firstOrNull { it.key.equals(token, ignoreCase = true) && it.value != defaultStoryboard }
            ?.let { return it.value }
    }
    // No flavor token matched → fall back to the build-type mapping (the default).
    for (token in tokens) {
        storyboardByConfig.entries.firstOrNull { it.key.equals(token, ignoreCase = true) }?.let { return it.value }
    }
    return defaultStoryboard
}

internal fun upsertSetting(buildSettings: String, key: String, value: String): String {
    val regex = Regex("""(^|\n)(\s*)$key\s*=\s*[^;]+;""")
    if (regex.containsMatchIn(buildSettings)) {
        // Lambda form: the returned string is used literally. The String-replacement
        // overload parses `$`/`\` as group refs/escapes, and `value` is often
        // `$(LAUNCH_SCREEN_STORYBOARD)` → `$(` is a malformed group ref → throws
        // "Illegal group reference". Rebuild the prefix from the captured groups.
        return regex.replace(buildSettings) { m ->
            m.groupValues[1] + m.groupValues[2] + key + " = \"" + value + "\";"
        }
    }
    val trimmed = buildSettings.trimEnd()
    val indent = "\t\t\t\t"
    return "$trimmed\n$indent$key = \"$value\";\n\t\t\t"
}

// ----- ID derivation -----

private fun stableId(seed: String): String {
    val md = MessageDigest.getInstance("SHA-1")
    val digest = md.digest("kmpnativesplash:$seed".toByteArray(Charsets.UTF_8))
    return digest.take(12).joinToString("") { "%02X".format(it.toInt() and 0xFF) }
}
