plugins {
    java
}

group = "com.eventopvp"
version = "1.2.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Versión fija de Paper (build reproducible). Al actualizar el servidor, cambia solo esta línea.
    val paper = "io.papermc.paper:paper-api:26.2.build.133-stable"
    compileOnly(paper)
    testImplementation(paper)
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(25) // requiere compilar con JDK 25
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filteringCharset = "UTF-8"
}

// Mundos base embebidos en el propio plugin (lobby + arenas).
// Viven en worlds-embed/*.zip y se empaquetan en el jar bajo worlds/.
// Al arrancar, el plugin los extrae a la carpeta del servidor la primera vez.
tasks.jar {
    val embed = file("worlds-embed")
    if (embed.isDirectory) {
        from(embed) {
            into("worlds")
            include("*.zip")
        }
    }
    // Resource pack de Bedrock (marcador lateral transparente). Se empaqueta bajo bedrock/.
    val bedrock = file("bedrock-resourcepack")
    if (bedrock.isDirectory) {
        from(bedrock) {
            into("bedrock")
            include("manifest.json", "ui/**")
        }
    }
}
