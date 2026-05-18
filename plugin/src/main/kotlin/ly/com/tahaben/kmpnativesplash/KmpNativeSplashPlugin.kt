package ly.com.tahaben.kmpnativesplash

import ly.com.tahaben.kmpnativesplash.agp.applyAgpHooks
import ly.com.tahaben.kmpnativesplash.dsl.SplashVariant
import ly.com.tahaben.kmpnativesplash.resolve.AndroidModuleOutcome
import ly.com.tahaben.kmpnativesplash.resolve.AndroidModuleResolver
import ly.com.tahaben.kmpnativesplash.resolve.GradleCandidateCollector
import ly.com.tahaben.kmpnativesplash.tasks.GenerateAndroidSplashTask
import ly.com.tahaben.kmpnativesplash.tasks.GenerateIosSplashTask
import ly.com.tahaben.kmpnativesplash.tasks.WireXcodeFlavorsTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import java.io.File

class KmpNativeSplashPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.logger.lifecycle("[kmp-native-splash] Applied to ${project.path}")
        val extension = project.extensions.create("kmpNativeSplash", KmpNativeSplashExtension::class.java).apply {
            // Default to writing into the on-disk Android source set (`src/<flavor>/res/`)
            // so users see the generated files in their checkout and any AGP variant picks
            // them up via the standard source-set merge. Flip to `true` to switch into the
            // AGP-integrated generated-dir path (`build/generated/kmpnativesplash/.../res/`).
            useGeneratedSourceSet.convention(false)
            // Default per-variant Android conventions (apply to the default variant; per-flavor
            // overrides can still set their own values).
            defaultVariant.androidBlock.applyManifestTheme.convention(true)
            defaultVariant.androidBlock.themeName.convention("@style/LaunchTheme")
            defaultVariant.androidBlock.resRoot.convention("src")
            defaultVariant.androidBlock.manifest.convention("src/androidMain/AndroidManifest.xml")
            defaultVariant.iosBlock.projectPath.convention("../iosApp")
            defaultVariant.iosBlock.targetName.convention("iosApp")
            // Default `ios.appDir = <projectPath>/<targetName>`. Stored as a string so the
            // convention is a `Provider<String>` joined from the two parts. Plugin resolves
            // the final File at task-wiring time against the module dir.
            defaultVariant.iosBlock.appDir.convention(
                defaultVariant.iosBlock.projectPath.zip(defaultVariant.iosBlock.targetName) { path, target ->
                    "$path/$target"
                },
            )
        }

        val aggregate = project.tasks.register("generateNativeSplash") { task ->
            task.group = "kmp-native-splash"
            task.description = "Generates all configured native splash assets."
        }

        // Standalone (non-AGP) registration: always present so the plugin is usable in
        // pure-Gradle contexts. When AGP is applied, additional per-variant tasks are
        // registered via the AGP hook (see below).
        val androidTaskWiring = mutableListOf<Pair<TaskProvider<GenerateAndroidSplashTask>, SplashVariant>>()
        registerVariantTasks(project, extension, extension.defaultVariant, aggregate, androidTaskWiring)
        extension.flavors.all { variant ->
            registerVariantTasks(project, extension, variant, aggregate, androidTaskWiring)
        }

        // AGP 9 dual-structure support. Resolve which module owns Android flavor config
        // (classic monolith == the applied module; AGP 9 split == a standalone
        // com.android.application module) and redirect ONLY the Android-writing tasks'
        // output dir + manifest target there. Source assets stay resolved against the
        // applied module (done in resolveSpec, see configureAndroidTask) — that is the
        // source-vs-output split the AGP-9 resolution design hinges on. Resolve in
        // afterEvaluate so the consumer's kmpNativeSplash {} block and sibling plugins {}
        // blocks have run; only plain values/Providers (closing over a File) land on
        // task inputs, so the configuration cache stays serializable.
        project.afterEvaluate {
            wireResolvedAndroidModule(project, extension, androidTaskWiring)
        }

        // AGP integration (milestone 4). Each AGP plugin id is checked — `withId` is a
        // no-op if the plugin is never applied, so this is safe in pure-KMP setups.
        listOf(
            "com.android.application",
            "com.android.library",
            "com.android.kotlin.multiplatform.library",
        ).forEach { agpId ->
            project.plugins.withId(agpId) {
                applyAgpHooks(project, extension, aggregate, ::configureAndroidTask)
            }
        }

        // iOS auto-wiring (milestone 5). Always-registered task that gates itself on
        // `kmpNativeSplash.ios.autoWireXcodeFlavors` so users can switch it on/off freely.
        registerXcodeWireTask(project, extension, aggregate)
    }

    private fun registerVariantTasks(
        project: Project,
        extension: KmpNativeSplashExtension,
        variant: SplashVariant,
        aggregate: TaskProvider<*>,
        androidTaskWiring: MutableList<Pair<TaskProvider<GenerateAndroidSplashTask>, SplashVariant>>,
    ) {
        val isDefault = variant.name == extension.defaultVariant.name
        val capName = if (isDefault) "" else variant.name.replaceFirstChar { it.uppercaseChar() }
        val androidTaskName = "generate${capName}AndroidSplash"
        val iosTaskName = "generate${capName}IosSplash"
        val variantTaskName = "generate${capName}NativeSplash"

        val androidEnabled = extension.platforms.android.getOrElse(true)
        val iosEnabled = extension.platforms.ios.getOrElse(true)

        val androidTask: TaskProvider<GenerateAndroidSplashTask>? = if (androidEnabled) {
            project.tasks.register(androidTaskName, GenerateAndroidSplashTask::class.java) { task ->
                task.group = "kmp-native-splash"
                task.description = "Generates Android splash assets for variant '${variant.name}'."
                configureAndroidTask(task, extension, variant, defaultOutputDir(project, extension, variant))
            }
        } else null
        androidTask?.let { androidTaskWiring += it to variant }

        val iosTask: TaskProvider<GenerateIosSplashTask>? = if (iosEnabled) {
            project.tasks.register(iosTaskName, GenerateIosSplashTask::class.java) { task ->
                task.group = "kmp-native-splash"
                task.description = "Generates iOS splash assets for variant '${variant.name}'."
                configureIosTask(task, extension, variant)
            }
        } else null

        if (isDefault) {
            aggregate.configure { umbrella ->
                androidTask?.let { umbrella.dependsOn(it) }
                iosTask?.let { umbrella.dependsOn(it) }
            }
        } else {
            val variantTask = project.tasks.register(variantTaskName) { task ->
                task.group = "kmp-native-splash"
                task.description = "Generates all splash assets for variant '${variant.name}'."
                androidTask?.let { task.dependsOn(it) }
                iosTask?.let { task.dependsOn(it) }
            }
            aggregate.configure { it.dependsOn(variantTask) }
        }
    }

    /**
     * Standalone output dir: when AGP is on the classpath the dir is purely a generated
     * scratch space (its contents aren't merged anywhere). When AGP is absent and
     * `useGeneratedSourceSet=false`, the dir flips to the on-disk source set path
     * (`<android.resRoot>/<variant>/res/`).
     *
     * Config-cache safety: we capture only `moduleDir` (a File) in the transformers; the
     * chain never closes over [Project], so the task's `outputDir` stays serializable.
     */
    private fun defaultOutputDir(
        project: Project,
        extension: KmpNativeSplashExtension,
        variant: SplashVariant,
    ): Provider<Directory> {
        val variantName = variant.name
        val isDefault = variantName == extension.defaultVariant.name
        val sourceSetName = if (isDefault) "androidMain" else variantName
        val moduleDir = project.projectDir
        val generatedDir = project.layout.buildDirectory.dir("generated/kmpnativesplash/$variantName/res")
        val sourceSetDir = project.layout.dir(
            extension.defaultVariant.androidBlock.resRoot.map { resRootPath ->
                resolveAgainstModule(moduleDir, resRootPath).resolve("$sourceSetName/res")
            },
        )
        // Honour `useGeneratedSourceSet` regardless of whether AGP is on the classpath —
        // users who want the files visible in `src/<flavor>/res/` should always get them
        // there, and AGP merges those automatically because it's a stock source set.
        return extension.useGeneratedSourceSet.orElse(false).flatMap { useGen ->
            if (useGen) generatedDir else sourceSetDir
        }
    }

    /**
     * Component C — runs once inside the plugin's `afterEvaluate`. Resolves the Android
     * application module (DSL override → auto-detect → fall back to the applied module),
     * logs one lifecycle/warn line, then redirects every standalone Android-writing task's
     * `outputDir` + `androidManifest` at the resolved module and arms `androidResolutionError`
     * on the ambiguous/not-found paths so the task — not `build` — fails with an actionable
     * message. Classic single-module projects resolve to the applied module, so the
     * redirect is a no-op and behaviour is byte-for-byte unchanged.
     */
    private fun wireResolvedAndroidModule(
        project: Project,
        extension: KmpNativeSplashExtension,
        androidTaskWiring: List<Pair<TaskProvider<GenerateAndroidSplashTask>, SplashVariant>>,
    ) {
        val explicit = extension.androidModule
        val r = AndroidModuleResolver.resolve(
            explicit,
            project.projectDir,
            GradleCandidateCollector.explicitMatchDir(project, explicit),
            GradleCandidateCollector.collect(project),
        )
        when (r.outcome) {
            AndroidModuleOutcome.Resolved,
            AndroidModuleOutcome.DslOverride,
            AndroidModuleOutcome.AppliedModuleIsTheMatch ->
                project.logger.lifecycle("[kmp-native-splash] ${r.message}")

            else -> project.logger.warn("[kmp-native-splash] ${r.message}")
        }

        val appliedDir = project.projectDir
        val androidDir = r.dir
        val isSplit = androidDir != null && androidDir != appliedDir
        val forceSourceSet = isSplit && extension.useGeneratedSourceSet.getOrElse(false)
        if (forceSourceSet) {
            project.logger.warn(
                "[kmp-native-splash] useGeneratedSourceSet=true is not supported across the " +
                        "AGP 9 module boundary; writing into $androidDir/<resRoot>/<sourceSet>/res instead.",
            )
        }
        val resolutionError = when (r.outcome) {
            AndroidModuleOutcome.Ambiguous, AndroidModuleOutcome.NotFound -> r.message
            else -> null
        }
        val manifestFile = androidDir?.let { resolveAndroidManifestFile(extension, it, appliedDir) }

        androidTaskWiring.forEach { (taskProvider, variant) ->
            taskProvider.configure { task ->
                task.outputDir.set(outputDirFor(project, extension, variant, androidDir, forceSourceSet, isSplit))
                if (resolutionError != null) task.androidResolutionError.set(resolutionError)
                if (manifestFile != null && manifestFile.exists()) task.androidManifest.set(manifestFile)
            }
        }
    }

    /**
     * Component D — output dir routed to the **resolved Android module**. Mirrors
     * [defaultOutputDir] but anchors the on-disk source-set path at [androidBaseDir]
     * instead of the applied module. [androidBaseDir] is null only on Ambiguous /
     * NotFound — a throw-away scratch path under `build/` is used and the task fails
     * fast via `androidResolutionError` before writing. [forceSourceSet] overrides
     * `useGeneratedSourceSet=true` to the source-set path (the AGP 9 split can't honour
     * a cross-project generated dir). [isSplit] (android module ≠ applied module)
     * selects the default variant's source set: `src/main/res` for a standalone
     * com.android.application module vs `src/androidMain/res` for the classic KMP
     * module. Config-cache safe: only [androidBaseDir] (a File) is captured by the
     * transformers.
     */
    private fun outputDirFor(
        project: Project,
        extension: KmpNativeSplashExtension,
        variant: SplashVariant,
        androidBaseDir: File?,
        forceSourceSet: Boolean,
        isSplit: Boolean,
    ): Provider<Directory> {
        val variantName = variant.name
        val isDefault = variantName == extension.defaultVariant.name
        // The default variant's source set differs by structure: the classic KMP module
        // (composeApp) uses the Kotlin Android source set `src/androidMain/res`, but a
        // standalone com.android.application module in the AGP 9 split is a plain Android
        // module with no `androidMain` — its default source set is `src/main/res`.
        // Flavors use the AGP product-flavor source set name (`src/<flavor>/res`) in both.
        val sourceSetName = when {
            !isDefault -> variantName
            isSplit -> "main"
            else -> "androidMain"
        }
        if (androidBaseDir == null) {
            return project.layout.buildDirectory.dir("kmpnativesplash/unresolved/$variantName/res")
        }
        val generatedDir = project.layout.buildDirectory.dir("generated/kmpnativesplash/$variantName/res")
        val sourceSetDir = project.layout.dir(
            extension.defaultVariant.androidBlock.resRoot.map { resRootPath ->
                resolveAgainstModule(androidBaseDir, resRootPath).resolve("$sourceSetName/res")
            },
        )
        if (forceSourceSet) return sourceSetDir
        return extension.useGeneratedSourceSet.orElse(false).flatMap { useGen ->
            if (useGen) generatedDir else sourceSetDir
        }
    }

    private fun hasAnyAgp(project: Project): Boolean =
        project.plugins.hasPlugin("com.android.application") ||
                project.plugins.hasPlugin("com.android.library") ||
                project.plugins.hasPlugin("com.android.kotlin.multiplatform.library")

    internal fun configureAndroidTask(
        task: GenerateAndroidSplashTask,
        extension: KmpNativeSplashExtension,
        variant: SplashVariant,
        outputDir: Provider<Directory>,
    ) {
        val spec = resolveSpec(extension, variant, task.project.projectDir)
        task.variantName.set(spec.variantName)
        task.defaultVariantInput.set(spec.isDefaultVariant)
        task.fullscreen.set(spec.fullscreen)

        spec.android.color?.let(task.color::set)
        spec.android.colorDark?.let(task.colorDark::set)
        spec.android.image?.let(task.image::set)
        spec.android.imageDark?.let(task.imageDark::set)
        spec.android.backgroundImage?.let(task.backgroundImage::set)
        spec.android.backgroundImageDark?.let(task.backgroundImageDark::set)
        task.gravity.set(spec.android.gravity)
        spec.android.screenOrientation?.let(task.screenOrientation::set)
        task.applyManifestTheme.set(spec.android.applyManifestTheme)
        task.themeName.set(spec.android.themeName)

        spec.branding?.let { branding ->
            (branding.androidImage ?: branding.image)?.let(task.brandingImage::set)
            (branding.androidImageDark ?: branding.imageDark)?.let(task.brandingImageDark::set)
            task.brandingMode.set(branding.mode)
            task.brandingBottomPadding.set(branding.androidBottomPadding ?: branding.bottomPadding)
        }

        val a12 = spec.android12
        task.hasAndroid12Block.set(a12 != null)
        a12?.let {
            it.color?.let(task.a12Color::set)
            it.colorDark?.let(task.a12ColorDark::set)
            it.image?.let(task.a12Image::set)
            it.imageDark?.let(task.a12ImageDark::set)
            it.iconBackgroundColor?.let(task.a12IconBackgroundColor::set)
            it.iconBackgroundColorDark?.let(task.a12IconBackgroundColorDark::set)
            it.brandingImage?.let(task.a12BrandingImage::set)
            it.brandingImageDark?.let(task.a12BrandingImageDark::set)
        }

        task.plistFiles.set(emptyList<String>())

        val moduleDir = task.project.projectDir
        val manifestPath = extension.defaultVariant.androidBlock.manifest.orNull
        val manifestFile = manifestPath?.let { resolveAgainstModule(moduleDir, it) }
        if (manifestFile != null && manifestFile.exists()) {
            task.androidManifest.set(manifestFile)
        }

        task.outputDir.set(outputDir)
    }

    private fun configureIosTask(
        task: GenerateIosSplashTask,
        extension: KmpNativeSplashExtension,
        variant: SplashVariant,
    ) {
        val spec = resolveSpec(extension, variant, task.project.projectDir)
        task.variantName.set(spec.variantName)
        task.defaultVariantInput.set(spec.isDefaultVariant)
        task.fullscreen.set(spec.fullscreen)
        task.autoWireXcodeFlavors.set(spec.ios.autoWireXcodeFlavors)

        spec.ios.color?.let(task.color::set)
        spec.ios.colorDark?.let(task.colorDark::set)
        spec.ios.image?.let(task.image::set)
        spec.ios.imageDark?.let(task.imageDark::set)
        spec.ios.backgroundImage?.let(task.backgroundImage::set)
        spec.ios.backgroundImageDark?.let(task.backgroundImageDark::set)
        task.contentMode.set(spec.ios.contentMode)
        task.infoPlistFiles.set(spec.ios.infoPlistFiles)

        spec.branding?.let { branding ->
            (branding.iosImage ?: branding.image)?.let(task.brandingImage::set)
            (branding.iosImageDark ?: branding.imageDark)?.let(task.brandingImageDark::set)
            task.brandingMode.set(branding.mode)
            task.brandingBottomPadding.set(branding.iosBottomPadding ?: branding.bottomPadding)
        }

        task.iosAppDir.fileProvider(iosAppDirProvider(task.project.projectDir, extension))
    }

    private fun registerXcodeWireTask(
        project: Project,
        extension: KmpNativeSplashExtension,
        aggregate: TaskProvider<*>,
    ) {
        // Build a ListProperty incrementally as flavors get registered. Each `.all { }`
        // callback fires at configuration time, so by cache-snapshot time the list holds
        // concrete strings — no Project/Extension capture for the configuration cache to
        // serialize. (project.provider { … extension.flavors.names } would have leaked
        // the project reference held by DefaultProvider.)
        val variantNamesList = project.objects.listProperty(String::class.java)
        variantNamesList.add(extension.defaultVariant.name)
        extension.flavors.all { variant -> variantNamesList.add(variant.name) }

        // Resolve `.xcodeproj` location as `<module>/<ios.projectPath>/<ios.targetName>.xcodeproj`
        // at execution time. The transformer captures only `moduleDir` (a File), so the
        // configuration cache can serialize it.
        val moduleDir = project.projectDir
        val iosBlock = extension.defaultVariant.iosBlock
        val xcodeprojFileProvider = iosBlock.projectPath.zip(iosBlock.targetName) { path, target ->
            resolveAgainstModule(moduleDir, path).resolve("$target.xcodeproj")
        }

        val task = project.tasks.register("wireXcodeFlavors", WireXcodeFlavorsTask::class.java) { t ->
            t.group = "kmp-native-splash"
            t.description = "Patches iosApp.xcodeproj to wire per-flavor LaunchScreen storyboards (opt-in)."
            t.autoWire.set(extension.defaultVariant.iosBlock.autoWireXcodeFlavors)
            t.xcodeproj.fileProvider(xcodeprojFileProvider)
            t.iosAppDir.fileProvider(iosAppDirProvider(moduleDir, extension))
            t.infoPlistFiles.set(extension.defaultVariant.iosBlock.infoPlistFiles)
            t.variantNames.set(variantNamesList)
            // Spec receives the Task at execution time; it captures only the parameter,
            // not the surrounding `extension` — so the configuration cache can serialize it.
            t.onlyIf("kmpNativeSplash.ios.autoWireXcodeFlavors is enabled") { task ->
                (task as WireXcodeFlavorsTask).autoWire.getOrElse(false)
            }
        }
        aggregate.configure { it.finalizedBy(task) }
    }
}

/**
 * Resolves a DSL path string against [moduleDir]. Absolute paths pass through unchanged;
 * relative paths anchor at the consuming module's directory. Top-level so the lambdas that
 * call it capture only their `moduleDir` arg — not a Plugin/Project reference — and stay
 * configuration-cache safe.
 */
private fun resolveAgainstModule(moduleDir: File, path: String): File {
    val asFile = File(path)
    return if (asFile.isAbsolute) asFile else File(moduleDir, path)
}

/**
 * Resolves the `AndroidManifest.xml` to patch against the **resolved Android module**
 * [androidBaseDir]. If the configured `android.manifest` path does not exist there AND
 * the Android module differs from the applied module (a true AGP 9 split, where
 * `:androidApp` is a stock `com.android.application` module), falls back to the canonical
 * standalone-app manifest `src/main/AndroidManifest.xml`. Classic single-module projects
 * (android == applied) keep the configured path verbatim — byte-for-byte legacy. The
 * returned file may not exist; callers patch only when it does.
 */
private fun resolveAndroidManifestFile(
    extension: KmpNativeSplashExtension,
    androidBaseDir: File,
    appliedDir: File,
): File? {
    val configured = extension.defaultVariant.androidBlock.manifest.orNull
    if (configured != null) {
        val f = resolveAgainstModule(androidBaseDir, configured)
        if (f.exists()) return f
    }
    if (androidBaseDir != appliedDir) {
        val mainManifest = File(androidBaseDir, "src/main/AndroidManifest.xml")
        if (mainManifest.exists()) return mainManifest
    }
    return configured?.let { resolveAgainstModule(androidBaseDir, it) }
}

/**
 * Builds a `Provider<File>` for the iOS app source directory from `ios.appDir`, resolved
 * against [moduleDir]. The configuration cache only needs to serialize [moduleDir] (a File)
 * plus the underlying Property — no Project/Extension reference is captured.
 */
private fun iosAppDirProvider(
    moduleDir: File,
    extension: KmpNativeSplashExtension,
): Provider<File> = extension.defaultVariant.iosBlock.appDir.map { path ->
    resolveAgainstModule(moduleDir, path)
}
