import io.github.liplum.mindustry.*
import java.time.LocalTime

plugins {
    kotlin("jvm") version "1.8.0"
    id("io.github.liplum.mgpp") version "1.2.0"
    id("maven-publish")
}

sourceSets {
    main {
        java.srcDirs("src")
    }
    test {
        java.srcDir("test")
    }
}
group= "me.mars"
version= "1.0"
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
repositories {
    mavenCentral()
    mindustryRepo()
}
dependencies {
    importMindustry()
}
mindustry {
    dependency {
        mindustry on "v143.1"
        arc on "v143.1"
    }
    meta.version = if (hasProperty("modVer")) property("modVer") as String else "build-${LocalTime.now()}"
    server {
        mindustry official "v143.1"
    }
    deploy {
        baseName = project.name
    }
}
mindustryAssets {
    root at "$projectDir/assets"
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "me.mars"
            artifactId = "rollback"
            version = "1.0"/*property("modVer") as String*/

            from(components["java"])
        }
    }
}