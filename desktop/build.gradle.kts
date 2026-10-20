import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

/** Fassung der Desktop-Pakete; MSI verlangt drei Zahlen. Muss zu versionName in app/build.gradle.kts passen. */
val appVersion = "1.5.0"

/** Plattformteil von FFmpeg: ohne Angabe der des bauenden Rechners, sonst -Psa.platform=linux-x86_64 usw. */
val nativePlatform: String = providers.gradleProperty("sa.platform").orNull ?: run {
    val os = System.getProperty("os.name").lowercase()
    when {
        os.contains("win") -> "windows-x86_64"
        os.contains("mac") -> "macosx-arm64"
        else -> "linux-x86_64"
    }
}

// Gemeinsamer Kern (Bibliothek, Kapitel, Export, Coversuche, Farbschemata) aus dem Android-Modul,
// Desktop-Oberfläche, Wiedergabe und Dateizugriff hier in src/main/kotlin
kotlin {
    jvmToolchain(17)
    sourceSets["main"].kotlin.srcDirs("../app/src/main/java", "src/main/kotlin")
    sourceSets["test"].kotlin.srcDirs("../app/src/test/java", "src/test/kotlin")
}

/** Fassung als Ressource, damit das Programm sie anzeigen und prüfen kann. */
val versionResource by tasks.registering {
    val out = layout.buildDirectory.dir("generated/version")
    inputs.property("version", appVersion)
    outputs.dir(out)
    doLast { out.get().file("app-version.txt").asFile.apply { parentFile.mkdirs(); writeText(appVersion) } }
}

sourceSets {
    main { resources.srcDir(versionResource) }
    test { resources.srcDir("../app/src/test/resources") }
}

// Texte kommen aus denselben strings.xml wie auf Android
tasks.processResources {
    from("../app/src/main/res/values/strings.xml") { into("strings/en") }
    from("../app/src/main/res/values-de/strings.xml") { into("strings/de") }
    // Zwei kleine Testdateien für den Selbsttest der fertigen Pakete
    from("../app/src/test/resources/fixtures") {
        include("nero.m4b", "chapters23.mp3")
        into("selbsttest")
    }
}

val ffmpegNatives: Configuration by configurations.creating
val iconsFull: Configuration by configurations.creating

/** Nur die Symbole, die die Oberfläche nennt (Icons.Rounded.X, Icons.AutoMirrored.Rounded.X). */
val usedIcons by tasks.registering(Jar::class) {
    val sources = fileTree("src/main/kotlin") { include("**/*.kt") }
    inputs.files(sources)
    archiveFileName.set("material-icons-used.jar")
    destinationDirectory.set(layout.buildDirectory.dir("icons-used"))
    includeEmptyDirs = false
    val wanted by lazy {
        val pattern = Regex("""Icons\.(AutoMirrored\.)?Rounded\.([A-Za-z0-9]+)""")
        sources.files.flatMap { f -> pattern.findAll(f.readText()).map { m ->
            (if (m.groupValues[1].isNotEmpty()) "automirrored/rounded/" else "rounded/") + m.groupValues[2] + "Kt"
        }.toList() }.toSet()
    }
    from({ zipTree(iconsFull.singleFile) }) {
        include { el ->
            val path = el.relativePath.pathString
            el.isDirectory || path.startsWith("META-INF/") || wanted.any { w ->
                path == "androidx/compose/material/icons/$w.class" || path.startsWith("androidx/compose/material/icons/$w\$")
            }
        }
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    // Vom großen Symbolpaket (38 MB) kommen nur die verwendeten Symbole ins Programm, siehe usedIcons
    compileOnly(compose.materialIconsExtended)
    iconsFull("org.jetbrains.compose.material:material-icons-extended-desktop:1.7.3") { isTransitive = false }
    implementation(files(usedIcons))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")

    // Wiedergabe und Untersuchen der Dateien: FFmpeg (LGPL-Fassung) über JavaCV
    implementation("org.bytedeco:javacv:1.5.11") { isTransitive = false }
    implementation("org.bytedeco:javacpp:1.5.11") { isTransitive = false }
    implementation("org.bytedeco:ffmpeg:7.1-1.5.11") { isTransitive = false }
    implementation("org.bytedeco:javacpp:1.5.11:$nativePlatform") { isTransitive = false }
    implementation("org.bytedeco:ffmpeg:7.1-1.5.11:$nativePlatform") { isTransitive = false }

    // Für Pakete anderer Systeme alle Plattformteile bereithalten (Aufgabe collectNatives)
    for (p in listOf("windows-x86_64", "linux-x86_64", "macosx-arm64")) {
        ffmpegNatives("org.bytedeco:javacpp:1.5.11:$p") { isTransitive = false }
        ffmpegNatives("org.bytedeco:ffmpeg:7.1-1.5.11:$p") { isTransitive = false }
    }

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

/** Legt die Plattformteile von FFmpeg und JavaCPP in build/natives ab. */
val collectNatives by tasks.registering(Copy::class) {
    from(ffmpegNatives)
    into(layout.buildDirectory.dir("natives"))
}

tasks.test {
    // Ohne Fenster und ohne Ton: Bilder werden außerhalb des Bildschirms gerechnet, Audio geht ins Leere
    systemProperty("java.awt.headless", "true")
    systemProperty("simpleaudiobooks.audio", "null")
    systemProperty("simpleaudiobooks.daten", layout.buildDirectory.dir("testdaten").get().asFile.absolutePath)
    systemProperty("org.bytedeco.javacpp.cachedir", layout.buildDirectory.dir("javacpp").get().asFile.absolutePath)
    maxHeapSize = "2g"
}

compose.desktop {
    application {
        mainClass = "app.simpleaudiobooks.desktop.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8", "-Xmx768m")
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Simple Audiobooks"
            packageVersion = appVersion
            description = "Free audiobook player"
            vendor = "Simple Audiobooks"
            copyright = "GPL-3.0-or-later"
            licenseFile.set(rootProject.file("LICENSE"))
            // AWT und Java Sound, XML für die Texte, HTTPS mit EC-Zertifikaten für die Coversuche
            modules("java.desktop", "java.xml", "java.logging", "java.management", "jdk.unsupported", "jdk.crypto.ec")
            windows {
                menuGroup = "Simple Audiobooks"
                upgradeUuid = "3f6b3a1e-8d2c-4b7a-9e51-6c0d2a7f4b19"
                perUserInstall = true
                dirChooser = true
                shortcut = true
                menu = true
                iconFile.set(project.file("icons/app.ico"))
            }
            macOS {
                bundleID = "app.simpleaudiobooks.desktop"
                appCategory = "public.app-category.music"
                iconFile.set(project.file("icons/app.icns"))
            }
            linux {
                packageName = "simple-audiobooks"
                appCategory = "AudioVideo"
                menuGroup = "AudioVideo;Audio;Player"
                shortcut = true
                iconFile.set(project.file("icons/app.png"))
            }
        }
    }
}

/** Erzeugt desktop/icons (PNG, ICO, ICNS) aus derselben Zeichnung wie das Fenstersymbol. */
val renderIcons by tasks.registering(JavaExec::class) {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("app.simpleaudiobooks.desktop.IconExportKt")
    args(project.file("icons").absolutePath)
    systemProperty("java.awt.headless", "true")
}

/** Bildschirmfotos für die README, ohne Fenster und ohne Ton: -Psa.demo=<neuer Ordner für die Beispielbibliothek> */
val screenshots by tasks.registering(JavaExec::class) {
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("app.simpleaudiobooks.desktop.ScreenshotsKt")
    args(layout.buildDirectory.dir("screenshots").get().asFile.absolutePath, providers.gradleProperty("sa.demo").getOrElse(""))
    systemProperty("java.awt.headless", "true")
    systemProperty("simpleaudiobooks.audio", "null")
    systemProperty("org.bytedeco.javacpp.cachedir", layout.buildDirectory.dir("javacpp").get().asFile.absolutePath)
    systemProperty("file.encoding", "UTF-8")
    maxHeapSize = "2g"
}
