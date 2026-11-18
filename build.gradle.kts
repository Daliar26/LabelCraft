plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
}

tasks.register("test") {
    group = "verification"
    description = "Runs unit tests for every Android module's debug variant."
}

gradle.projectsEvaluated {
    val unitTestTasks = subprojects.mapNotNull { it.tasks.findByName("testDebugUnitTest") }
    tasks.named("test") { dependsOn(unitTestTasks) }
}

tasks.register("clean", Delete::class) { delete(rootProject.layout.buildDirectory) }
