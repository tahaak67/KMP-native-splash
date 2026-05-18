package ly.com.tahaben.kmpnativesplash.agp

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Variant
import ly.com.tahaben.kmpnativesplash.KmpNativeSplashExtension
import ly.com.tahaben.kmpnativesplash.dsl.SplashVariant
import ly.com.tahaben.kmpnativesplash.tasks.GenerateAndroidSplashTask
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

/**
 * Per-AGP-variant task registration. Called once per detected AGP plugin (app/library/KMP).
 * For each AGP variant we:
 *  1. Pick the matching kmpNativeSplash variant (by product-flavor name, then variant name, else default).
 *  2. Register `generate<Variant>AndroidSplash` writing to `build/generated/kmpnativesplash/<variant>/res/`.
 *  3. Register that output as a generated res dir via `variant.sources.res.addGeneratedSourceDirectory`,
 *     so AGP merges it into `processVariantAndroidResources` automatically.
 */
internal fun applyAgpHooks(
    project: Project,
    extension: KmpNativeSplashExtension,
    aggregate: TaskProvider<*>,
    configure: (GenerateAndroidSplashTask, KmpNativeSplashExtension, SplashVariant, Provider<Directory>) -> Unit,
) {
    val androidComponents = project.extensions.findByType(AndroidComponentsExtension::class.java)
    if (androidComponents == null) {
        project.logger.warn("[kmp-native-splash] AGP detected but androidComponents extension missing; skipping integration.")
        return
    }

    androidComponents.onVariants { variant ->
        // Check the flag here (not at applyAgpHooks) so the user's `kmpNativeSplash { useGeneratedSourceSet=… }`
        // block has had a chance to run. When false (default), the standalone `generate*AndroidSplash`
        // tasks write into `src/<flavor>/res/` and AGP picks them up via standard source-set resolution
        // — registering AGP-driven duplicates here would just double-write into `build/`.
        if (!extension.useGeneratedSourceSet.getOrElse(false)) {
            return@onVariants
        }
        // AGP 9 split: when the applied module is the KMP `shared` library
        // (com.android.kotlin.multiplatform.library) and not itself the application
        // module, AGP's per-variant source wiring here would target the library's
        // variants, not :androidApp. The fragile source wiring must never cross a
        // project boundary — the standalone generate*AndroidSplash tasks (redirected to
        // the resolved Android module's source set by wireResolvedAndroidModule) are the
        // supported route. Leave the classic com.android.application / com.android.library
        // paths untouched.
        val appliedIsApp = project.plugins.hasPlugin("com.android.application")
        val appliedIsKmpLib = project.plugins.hasPlugin("com.android.kotlin.multiplatform.library")
        if (appliedIsKmpLib && !appliedIsApp) {
            project.logger.info(
                "[kmp-native-splash] AGP 9 split detected; skipping AGP generated-source " +
                        "wiring for variant '${variant.name}' (handled via the resolved Android module's source set).",
            )
            return@onVariants
        }
        val splashVariant = pickSplashVariant(extension, variant)
        val taskName = "generate${variant.name.replaceFirstChar { it.uppercaseChar() }}AndroidSplash"
        if (project.tasks.findByName(taskName) != null) {
            // Pre-existing standalone task with the same name — skip the AGP wiring to avoid
            // a duplicate registration. Users hitting this should rename the kmpNativeSplash flavor.
            project.logger.warn("[kmp-native-splash] Task '$taskName' already registered; AGP wiring skipped for variant '${variant.name}'.")
            return@onVariants
        }
        val outDir: Provider<Directory> =
            project.layout.buildDirectory.dir("generated/kmpnativesplash/${variant.name}/res")
        val taskProvider = project.tasks.register(taskName, GenerateAndroidSplashTask::class.java) { task ->
            task.group = "kmp-native-splash"
            task.description = "Generates Android splash assets for AGP variant '${variant.name}'."
            configure(task, extension, splashVariant, outDir)
        }
        @Suppress("UnstableApiUsage")
        variant.sources.res?.addGeneratedSourceDirectory(taskProvider) { it.outputDir }
        aggregate.configure { it.dependsOn(taskProvider) }
    }
}

private fun pickSplashVariant(extension: KmpNativeSplashExtension, variant: Variant): SplashVariant {
    // 1. Try each product-flavor name in declaration order.
    variant.productFlavors.forEach { (_, flavorName) ->
        extension.flavors.findByName(flavorName)?.let { return it }
    }
    // 2. Try the full variant name (covers cases like buildType-only variants).
    extension.flavors.findByName(variant.name)?.let { return it }
    // 3. Default to the base extension config.
    return extension.defaultVariant
}
