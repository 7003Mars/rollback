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
    withJavadocJar()
    withSourcesJar()
}

artifacts {
    archives(tasks.getByName("sourcesJar"))
}

repositories {
    mavenCentral()
    maven("https://maven.xpdustry.com/anuken")
    mindustryRepo()
}
dependencies {
    importMindustry()
}
configurations.all{
    resolutionStrategy.eachDependency {
        if(this.requested.group == "com.github.Anuken.Arc"){
            this.useVersion("v146")
        }
    }
}

mindustry {
    projectType = ProjectType.Plugin
    dependency {
        mindustry on "v146"
        arc on "v146"
    }
    meta.version = if (hasProperty("modVer")) property("modVer") as String else "build-${LocalTime.now()}"
    server {
        mindustry official "v146"
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
            version = "1.2"/*property("modVer") as String*/

            from(components["java"])
        }
    }
}

configurations.all{
    resolutionStrategy.eachDependency {
        if(this.requested.group == "com.github.Anuken.Arc"){
            this.useVersion("v146")
        }
    }
}