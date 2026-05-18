package ly.com.tahaben.kmpsplash.resolve

import java.io.File

/** One project in the build graph, reduced to the facts the resolver needs. */
data class CandidateProject(
    val path: String,                   // Gradle path, e.g. ":androidApp" or ":" for root
    val projectDir: File,
    val isApplied: Boolean,             // the module the plugin is applied to
    val hasAndroidApplication: Boolean, // plugins.hasPlugin("com.android.application")
    val hasKmpLibraryPlugin: Boolean,   // plugins.hasPlugin("com.android.kotlin.multiplatform.library")
)

enum class AndroidModuleOutcome {
    DslOverride, Resolved, AppliedModuleIsTheMatch,
    FellBackToAppliedModule, Ambiguous, NotFound,
}

data class AndroidModuleResolution(
    val dir: File?,                     // null only for Ambiguous / NotFound
    val outcome: AndroidModuleOutcome,
    val message: String,               // lifecycle note, warning, or actionable error
)

object AndroidModuleResolver {
    fun resolve(
        explicitPath: String?,
        appliedProjectDir: File,
        explicitMatchDir: File?,        // caller resolves rootProject.findProject(explicitPath)?.projectDir
        candidates: List<CandidateProject>,
    ): AndroidModuleResolution {
        if (explicitPath != null) {
            return if (explicitMatchDir != null) {
                AndroidModuleResolution(
                    explicitMatchDir, AndroidModuleOutcome.DslOverride,
                    "Using androidModule = \"$explicitPath\" -> $explicitMatchDir"
                )
            } else {
                AndroidModuleResolution(
                    null, AndroidModuleOutcome.NotFound,
                    "androidModule = \"$explicitPath\" does not resolve to a project in this " +
                            "build. Use a Gradle project path like \":androidApp\" and ensure the " +
                            "module is included in settings.gradle(.kts)."
                )
            }
        }
        // KMP-library modules can never host productFlavors → exclude them.
        val appModules = candidates.filter { it.hasAndroidApplication && !it.hasKmpLibraryPlugin }
        return when {
            appModules.isEmpty() -> AndroidModuleResolution(
                appliedProjectDir, AndroidModuleOutcome.FellBackToAppliedModule,
                "No com.android.application module found; using the applied module " +
                        "($appliedProjectDir). For the AGP 9 split structure set " +
                        "androidModule = \":yourAndroidApp\"."
            )

            appModules.size == 1 -> appModules.single().let { only ->
                if (only.isApplied)
                    AndroidModuleResolution(
                        only.projectDir,
                        AndroidModuleOutcome.AppliedModuleIsTheMatch,
                        "Android files target the applied module (${only.projectDir})."
                    )
                else
                    AndroidModuleResolution(
                        only.projectDir, AndroidModuleOutcome.Resolved,
                        "Auto-detected Android application module \"${only.path}\" -> ${only.projectDir}."
                    )
            }

            else -> AndroidModuleResolution(
                null, AndroidModuleOutcome.Ambiguous,
                "Found ${appModules.size} com.android.application modules: " +
                        "${appModules.joinToString { it.path }}. Set androidModule = \"<path>\"."
            )
        }
    }
}
