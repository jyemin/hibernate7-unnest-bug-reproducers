plugins {
    java
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    testImplementation("org.hibernate.orm:hibernate-core:7.3.4.Final")
    testImplementation("com.h2database:h2:2.3.232")
    // Driver is required so Hibernate can register the dialect; no live PG server is used —
    // the nested-EXISTS reproducer runs HQL-→SQM-→SQL-AST conversion only, which fails before
    // any SQL execution.
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
