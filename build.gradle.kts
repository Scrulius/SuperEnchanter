/*
 * SuperEnchanter - Custom Anvil & Enchanting Table plugin
 * Author: Scrulius (GitHub)
 * License: All Rights Reserved
 */

plugins {
    java
}

group = "dev.scrulius"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")     // Paper API
    maven("https://jitpack.io")                                    // Vault
    maven("https://repo.rosewooddev.io/repository/public/")       // PlayerPoints
}

dependencies {
    // Paper API 26.1.2 (versionado year-based con build identifier; usa .build.+ para el último)
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.69-stable")

    // Jars locales por comodín (fileTree) en vez de nombre exacto: al actualizar el plugin
    // cambia la versión en el nombre del jar y un path fijo rompería el build. OJO: no dejes
    // DOS versiones del mismo plugin en la carpeta o ambas entrarían al classpath.
    val serverPlugins = "C:/Users/Knopp/Desktop/server paper testeos/plugins"

    // Familia eco (eco + EcoEnchants + EcoSkills + Talismans) — contra los jars DEL SERVER,
    // no el repo de Auxilor con "+": el 2026-07 Auxilor publicó las 2026.x con los paquetes
    // reorganizados y el comodín rompía el build sin tocar código (le pasó a SuperMines).
    // Compilar contra lo que corre el server = nunca desincronizado.
    // (Talismans aún sin imports, pero SE VA A USAR — no la quites.)
    compileOnly(fileTree(serverPlugins) { include("eco-*.jar") })
    compileOnly(fileTree(serverPlugins) { include("EcoEnchants*.jar") })
    compileOnly(fileTree(serverPlugins) { include("EcoSkills*.jar") })
    compileOnly(fileTree(serverPlugins) { include("Talismans*.jar") })

    // MythicMobs (softdepend) - librerías encantadas (bloques marcados)
    compileOnly(fileTree(serverPlugins) { include("MythicMobs*.jar") })

    // SuperCore (softdepend) - puente a EcoSkills para la skill Magia (MagiaService).
    // Referencia el jar del plugin hermano; degrada solo si SuperCore no está en runtime.
    compileOnly(fileTree("C:/Users/Knopp/plugins/SuperCore/build/libs") { include("SuperCore-*.jar") })

    // PlaceholderAPI (softdepend) - expone los bonus de Magia (%superenchanter_magia_*%)
    // para que la descripción de la skill (y scoreboards, etc.) muestren valores reales.
    compileOnly(fileTree(serverPlugins) { include("PlaceholderAPI-*.jar") })

    // Vault (softdepend) - economía monetaria
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")

    // PlayerPoints (softdepend) - economía de tokens
    compileOnly("org.black_ixx:playerpoints:3.2.7")

    // Tests — JUnit 5. Cubren la matemática PURA y Bukkit-free (AnvilFormulas,
    // EnchantFormulas). No requieren Paper en el classpath: por eso las fórmulas
    // viven en clases sin imports de Bukkit.
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // MockBukkit — servidor Bukkit/Paper simulado para testear la lógica que toca
    // la API real (merge del yunque con DataComponents, escáner de librerías).
    // NO cubre lo que llama a EcoEnchants (com.willfp.* es compileOnly → ausente
    // en runtime de test). paper-api no se hereda del sourceset main (allí es
    // compileOnly), así que hay que declararla explícitamente para los tests.
    testImplementation("io.papermc.paper:paper-api:26.1.2.build.69-stable")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v26.1.2:4.113.1")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.test {
    useJUnitPlatform()
}

