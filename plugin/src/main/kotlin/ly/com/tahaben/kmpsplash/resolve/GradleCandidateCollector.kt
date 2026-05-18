package ly.com.tahaben.kmpsplash.resolve

internal object GradleCandidateCollector {
    private const val ANDROID_APP = "com.android.application"
    private const val KMP_LIBRARY = "com.android.kotlin.multiplatform.library"

    fun collect(applied: org.gradle.api.Project): List<CandidateProject> {
        val root = applied.rootProject
        return (listOf(root) + root.subprojects).map { p ->
            CandidateProject(
                path = p.path,
                projectDir = p.projectDir,
                isApplied = p == applied,
                hasAndroidApplication = p.plugins.hasPlugin(ANDROID_APP),
                hasKmpLibraryPlugin = p.plugins.hasPlugin(KMP_LIBRARY),
            )
        }
    }

    fun explicitMatchDir(applied: org.gradle.api.Project, explicitPath: String?): java.io.File? =
        explicitPath?.let { applied.rootProject.findProject(it)?.projectDir }
}
