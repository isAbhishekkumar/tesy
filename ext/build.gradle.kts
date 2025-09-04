plugins {
    id("java-library")
    id("maven-publish")
    alias(libs.plugins.gradle.shadow)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
}

dependencies {
    compileOnly(libs.echo.common)
    compileOnly(libs.kotlin.stdlib)
    implementation(libs.newpipe.extractor)
    
    // Add Android compatibility dependencies
    compileOnly("androidx.annotation:annotation:1.8.2")
    compileOnly("androidx.core:core:1.12.0")
    
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.echo.common)
    testImplementation(libs.newpipe.extractor)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

// Extension properties goto `gradle.properties` to set values

val extType: String by project
val extId: String by project
val extClass: String by project

val extIconUrl: String? by project
val extName: String by project
val extDescription: String? by project

val extAuthor: String by project
val extAuthorUrl: String? by project

val extRepoUrl: String? by project
val extUpdateUrl: String? by project

val gitHash = execute("git", "rev-parse", "HEAD").take(7)
val gitCount = execute("git", "rev-list", "--count", "HEAD").toInt()
val verCode = gitCount
val verName = "v$gitHash"

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "dev.brahmkshatriya.echo.extension"
            artifactId = extId
            version = verName

            from(components["java"])
        }
    }
}

tasks {
    shadowJar {
        archiveBaseName.set(extId)
        archiveVersion.set(verName)
        manifest {
            attributes(
                mapOf(
                    "Extension-Id" to extId,
                    "Extension-Type" to extType,
                    "Extension-Class" to extClass,

                    "Extension-Version-Code" to verCode,
                    "Extension-Version-Name" to verName,

                    "Extension-Icon-Url" to extIconUrl,
                    "Extension-Name" to extName,
                    "Extension-Description" to extDescription,

                    "Extension-Author" to extAuthor,
                    "Extension-Author-Url" to extAuthorUrl,

                    "Extension-Repo-Url" to extRepoUrl,
                    "Extension-Update-Url" to extUpdateUrl
                )
            )
        }
    }
    
     // Disable test tasks for now
    test {
        enabled = false
    }
    
    compileTestKotlin {
        enabled = false
    }
}

// Add proguard configuration for the ext module
tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    mergeServiceFiles()
    manifest {
        attributes(mapOf("Main-Class" to "dev.brahmkshatriya.echo.extension.YouTubeMusicExtension"))
    }
    // Exclude problematic signatures and files
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
    exclude("META-INF/maven/**")
    exclude("META-INF/DEPENDENCIES")
    exclude("META-INF/LICENSE")
    exclude("META-INF/LICENSE.txt")
    exclude("META-INF/NOTICE")
    exclude("META-INF/NOTICE.txt")
    exclude("META-INF/versions/9/**")
    exclude("META-INF/services/javax.script.ScriptEngineFactory")
}


fun execute(vararg command: String): String = providers.exec {
    commandLine(*command)
}.standardOutput.asText.get().trim()