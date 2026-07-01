import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

// Applied from composeApp/build.gradle.kts — sets rootProject.extra for versionCode/versionName.

val versionPropertiesFile = rootProject.file("version.properties")

fun bumpSemverPatch(versionName: String): String {
    val parts = versionName.split(".")
    return when {
        parts.size >= 3 -> {
            val patch = parts.last().toIntOrNull() ?: 0
            parts.dropLast(1).joinToString(".") + "." + (patch + 1)
        }
        parts.size == 2 -> "${parts[0]}.${parts[1]}.1"
        else -> if (versionName.isBlank()) "1.0.0" else "$versionName.1"
    }
}

if (versionPropertiesFile.exists()) {
    val skipBump =
        providers.gradleProperty("lifeexp.skipVersionBump").orNull == "true" ||
            System.getenv("LIFEEXP_SKIP_VERSION_BUMP") == "true"

    val releasePackagingTask = gradle.startParameter.taskNames.any { task ->
        val t = task.lowercase()
        t.contains("release") &&
            !t.contains("debug") &&
            (t.contains("assemble") || t.contains("bundle") || t.contains("install") || t.contains("publish"))
    }

    if (
        releasePackagingTask &&
        !skipBump &&
        !gradle.startParameter.isDryRun &&
        !rootProject.extra.has("lifeexp.releaseVersionBumped")
    ) {
        val props = Properties()
        FileInputStream(versionPropertiesFile).use { props.load(it) }
        val previousCode = props.getProperty("VERSION_CODE")?.toIntOrNull() ?: 0
        val previousName = props.getProperty("VERSION_NAME") ?: "1.0.0"
        val newCode = previousCode + 1
        val newName = bumpSemverPatch(previousName)
        props.setProperty("VERSION_CODE", newCode.toString())
        props.setProperty("VERSION_NAME", newName)
        FileOutputStream(versionPropertiesFile).use {
            props.store(it, "Auto-bumped on release build — commit after publishing")
        }
        logger.lifecycle(
            "LifeExp release version: versionCode $previousCode -> $newCode, " +
                "versionName $previousName -> $newName",
        )
        rootProject.extra["lifeexp.releaseVersionBumped"] = true
    }
}

val versionProps = Properties()
if (versionPropertiesFile.exists()) {
    versionPropertiesFile.inputStream().use { versionProps.load(it) }
}
rootProject.extra["lifeexp.appVersionCode"] =
    versionProps.getProperty("VERSION_CODE")?.toIntOrNull() ?: 1
rootProject.extra["lifeexp.appVersionName"] =
    versionProps.getProperty("VERSION_NAME") ?: "1.0.0"
