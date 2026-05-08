plugins {
	kotlin("jvm") version "2.2.21"
	kotlin("plugin.spring") version "2.2.21"
	id("org.springframework.boot") version "4.0.6"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.tinkermode"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {

	//core
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.jetbrains.kotlin:kotlin-reflect")

	// Jackson - kotlin support
	implementation("tools.jackson.module:jackson-module-kotlin")

	// Kotlin coroutines (Channels, worker pool, batcher)
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

	/*// OpenAPI / Swagger UI
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")

	// Structured JSON logging
	implementation("net.logstash.logback:logstash-logback-encoder:9.0")
*/

	// Test - single starter covers everything (JUnit5, AssertJ, MockMvc etc.)
	testImplementation("org.springframework.boot:spring-boot-starter-test")
//	{
//		exclude(module = "mockito-core")   // using MockK instead
//	}
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")


}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()

	testLogging {
		events("passed", "skipped", "failed")
	}
}
