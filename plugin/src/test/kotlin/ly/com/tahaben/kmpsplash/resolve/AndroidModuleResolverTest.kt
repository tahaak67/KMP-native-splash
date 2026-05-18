package ly.com.tahaben.kmpsplash.resolve

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Authoritative coverage for the AGP 9 Android-module resolver: one case per outcome
 * plus the KMP-library-exclusion edge. The resolver is Gradle-free, so this is plain
 * JUnit (TestKit can't apply real AGP without an Android SDK on the classpath).
 */
class AndroidModuleResolverTest {

    private val appliedDir = File("/build/shared")
    private val appDir = File("/build/androidApp")

    private fun candidate(
        path: String,
        dir: File,
        applied: Boolean = false,
        app: Boolean = false,
        kmpLib: Boolean = false,
    ) = CandidateProject(path, dir, applied, app, kmpLib)

    @Test
    fun `explicit androidModule that resolves wins`() {
        val r = AndroidModuleResolver.resolve(
            explicitPath = ":androidApp",
            appliedProjectDir = appliedDir,
            explicitMatchDir = appDir,
            candidates = listOf(
                candidate(":shared", appliedDir, applied = true, kmpLib = true),
                candidate(":androidApp", appDir, app = true),
            ),
        )
        assertEquals(AndroidModuleOutcome.DslOverride, r.outcome)
        assertEquals(appDir, r.dir)
        assertTrue(r.message.contains(":androidApp"))
    }

    @Test
    fun `explicit androidModule that does not resolve is NotFound`() {
        val r = AndroidModuleResolver.resolve(
            explicitPath = ":nope",
            appliedProjectDir = appliedDir,
            explicitMatchDir = null,
            candidates = listOf(candidate(":shared", appliedDir, applied = true, kmpLib = true)),
        )
        assertEquals(AndroidModuleOutcome.NotFound, r.outcome)
        assertNull(r.dir)
        assertTrue(r.message.contains("settings.gradle"))
    }

    @Test
    fun `single non-applied app module is auto-detected (AGP 9 split)`() {
        val r = AndroidModuleResolver.resolve(
            explicitPath = null,
            appliedProjectDir = appliedDir,
            explicitMatchDir = null,
            candidates = listOf(
                candidate(":shared", appliedDir, applied = true, kmpLib = true),
                candidate(":androidApp", appDir, app = true),
            ),
        )
        assertEquals(AndroidModuleOutcome.Resolved, r.outcome)
        assertEquals(appDir, r.dir)
        assertTrue(r.message.contains(":androidApp"))
    }

    @Test
    fun `applied module being the sole app module is the classic monolith`() {
        val r = AndroidModuleResolver.resolve(
            explicitPath = null,
            appliedProjectDir = appliedDir,
            explicitMatchDir = null,
            candidates = listOf(candidate(":composeApp", appliedDir, applied = true, app = true)),
        )
        assertEquals(AndroidModuleOutcome.AppliedModuleIsTheMatch, r.outcome)
        assertEquals(appliedDir, r.dir)
    }

    @Test
    fun `no app module anywhere falls back to the applied module`() {
        val r = AndroidModuleResolver.resolve(
            explicitPath = null,
            appliedProjectDir = appliedDir,
            explicitMatchDir = null,
            candidates = listOf(candidate(":shared", appliedDir, applied = true)),
        )
        assertEquals(AndroidModuleOutcome.FellBackToAppliedModule, r.outcome)
        assertEquals(appliedDir, r.dir)
    }

    @Test
    fun `more than one app module is Ambiguous`() {
        val r = AndroidModuleResolver.resolve(
            explicitPath = null,
            appliedProjectDir = appliedDir,
            explicitMatchDir = null,
            candidates = listOf(
                candidate(":shared", appliedDir, applied = true, kmpLib = true),
                candidate(":androidApp", appDir, app = true),
                candidate(":wearApp", File("/build/wearApp"), app = true),
            ),
        )
        assertEquals(AndroidModuleOutcome.Ambiguous, r.outcome)
        assertNull(r.dir)
        assertTrue(r.message.contains(":androidApp"))
        assertTrue(r.message.contains(":wearApp"))
    }

    @Test
    fun `module applying both app and KMP library plugin is excluded from the app set`() {
        // The only com.android.application module also applies the KMP library plugin,
        // so it can't host productFlavors → excluded → no app modules remain → fallback.
        val r = AndroidModuleResolver.resolve(
            explicitPath = null,
            appliedProjectDir = appliedDir,
            explicitMatchDir = null,
            candidates = listOf(
                candidate(":shared", appliedDir, applied = true),
                candidate(":hybrid", appDir, app = true, kmpLib = true),
            ),
        )
        assertEquals(AndroidModuleOutcome.FellBackToAppliedModule, r.outcome)
        assertEquals(appliedDir, r.dir)
    }
}
