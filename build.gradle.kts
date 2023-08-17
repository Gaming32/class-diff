plugins {
    java
    `java-library`
}

group = "io.github.prcraftmc"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api("org.ow2.asm:asm:9.5")
    api("org.ow2.asm:asm-tree:9.5")

    compileOnly("org.jetbrains:annotations:24.0.1")

    api("com.nothome:javaxdelta:2.0.1")
    api("io.github.java-diff-utils:java-diff-utils:4.12")

//    implementation("net.lenni0451:Reflect:1.2.4")

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}
