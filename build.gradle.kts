plugins {
    java
}

repositories {
    // mavenLocal() is here so you can verify a Hibernate SNAPSHOT containing a fix by
    // bumping the hibernate-core coordinate below and running `./gradlew publishToMavenLocal`
    // in your local hibernate-orm checkout.
    mavenLocal()
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    testImplementation("org.hibernate.orm:hibernate-core:7.3.4.Final")
    testImplementation("org.postgresql:postgresql:42.7.4")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}
