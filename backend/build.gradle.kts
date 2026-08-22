plugins {
    id("java")
    id("org.sonarqube") version "6.0.1.5171"
    id("org.springframework.boot") version "3.3.3"
    id("io.spring.dependency-management") version "1.1.5"
    jacoco
}

group = "com.centralizesys"
version = "0.0.2-SNAPSHOT"

// CRITICAL FIX: Ensure we are strictly using Java 21 Toolchain
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}



dependencyLocking {
    lockAllConfigurations()
}

// CRITICAL FIX: IntelliJ injects ephemeral configurations for testing and debugging (e.g., ijJvmDebugger)
// If we lock all configurations globally, Gradle will fail to create tasks in IntelliJ because these
// injected configurations are not in the gradle.lockfile. We must disable locking for them.
configurations.matching { it.name.startsWith("ij") || it.name.startsWith("idea") }.configureEach {
    resolutionStrategy.deactivateDependencyLocking()
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("me.paulschwarz:spring-dotenv:4.0.0")

    // PostgreSQL JDBC driver
    implementation("org.postgresql:postgresql")

    // Flyway for Database Migrations
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    implementation("org.hibernate.validator:hibernate-validator")

    // Security
    implementation("org.springframework.boot:spring-boot-starter-security")

    // Caching (Spring Cache abstraction + Caffeine as provider)
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.5")

    // CRITICAL FIX: Force Lombok 1.18.36 to prevent "ExceptionInInitializerError" on Java 21+
    compileOnly("org.projectlombok:lombok:1.18.36")
    annotationProcessor("org.projectlombok:lombok:1.18.36")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.springframework.security:spring-security-test")

    // Testcontainers for PostgreSQL (Integration Tests)
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")

    // Updated to 5.4.0 to resolve CVE-2025-31672, CVE-2024-25710, CVE-2024-26308
    // Excel (Apache POI)
    implementation("org.apache.poi:poi-ooxml:5.4.0")

    // CRITICAL FIX: Force commons-lang3 3.18.0 to resolve CVE-2025-48924
    implementation("org.apache.commons:commons-lang3:3.18.0")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed")
    }
    maxHeapSize = "2048m"
}

// CRITICAL FIX: Ensure bootRun uses project root as working directory
// This ensures the database is created in the same location (data/) whether
// running from IntelliJ or via ./gradlew bootRun
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    workingDir = rootProject.projectDir.parentFile
}

// CRITICAL FIX: Handle Spanish accents in source code
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

sonar {
    properties {
        property("sonar.projectKey", "Valarcos_sinpen_thesis")
        property("sonar.organization", "valarcos")
        property("sonar.host.url", "https://sonarcloud.io")
    }
}