// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.ksp) apply false
}

allprojects {
    // Redirect intermediate build files to a root tmp folder
    layout.buildDirectory.set(rootProject.layout.projectDirectory.dir("tmp/${project.name}"))
}

tasks.register<Delete>("cleanTmp") {
    delete(rootProject.layout.projectDirectory.dir("tmp"))
}