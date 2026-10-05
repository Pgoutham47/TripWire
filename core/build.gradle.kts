plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
}

tasks.test {
    testLogging {
        events("failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Model pipeline helpers (tools/model/README.md). Paths are relative to the repository root.
val modelData = rootProject.layout.projectDirectory.dir("tools/model/data/build")

tasks.register<JavaExec>("exportTraining") {
    group = "tripwire model"
    description = "Writes chat-format training data with the app's own prompt."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.tripwire.core.tools.ExportTraining")
    doFirst {
        val split = (project.findProperty("split") as String?) ?: "train"
        args(modelData.file("$split.jsonl").asFile.path, modelData.file("$split.chat.jsonl").asFile.path)
    }
}

tasks.register<JavaExec>("evalTactics") {
    group = "tripwire model"
    description = "Scores keyword rules (-Ppredictions=rules) or a predictions file on a split."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.tripwire.core.tools.TacticEval")
    doFirst {
        val split = (project.findProperty("split") as String?) ?: "test"
        val preds = (project.findProperty("predictions") as String?) ?: "rules"
        val name = if (preds == "rules") "rules" else File(preds).nameWithoutExtension
        args(modelData.file("$split.jsonl").asFile.path, preds, modelData.file("report-$name-$split.json").asFile.path)
    }
}

tasks.register<JavaExec>("scenarioModelEval") {
    group = "tripwire model"
    description = "Replays the scenarios with a model as tactic reader: -Pmode=prompts or -Pmode=replay -Ppredictions=<file>."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.tripwire.core.tools.ScenarioModelEval")
    doFirst {
        val mode = (project.findProperty("mode") as String?) ?: "prompts"
        val file = (project.findProperty("predictions") as String?) ?: modelData.file("scenarios.chat.jsonl").asFile.path
        args(mode, file)
    }
}
