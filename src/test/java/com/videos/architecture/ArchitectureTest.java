package com.videos.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/** Clean-architecture rules. A violation fails the build. */
@AnalyzeClasses(packages = "com.videos", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

	private static final String[] FRAMEWORKS = { "org.springframework..", "jakarta.persistence..", "jakarta.ws.rs..",
			"jakarta.servlet..", "org.hibernate..", "tools.jackson..", "com.fasterxml..",
			"org.mapstruct..", "io.swagger..", "com.platform.." };

	@ArchTest
	static final ArchRule dependenciesPointInward = layeredArchitecture().consideringOnlyDependenciesInLayers()
			.layer("Domain").definedBy("com.videos.domain..")
			.layer("Application").definedBy("com.videos.application..")
			.layer("Infrastructure").definedBy("com.videos.infrastructure..")
			.layer("Interfaces").definedBy("com.videos.interfaces..")
			.whereLayer("Interfaces").mayNotBeAccessedByAnyLayer()
			.whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
			.whereLayer("Application").mayOnlyBeAccessedByLayers("Interfaces", "Infrastructure")
			.whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure", "Interfaces");

	@ArchTest
	static final ArchRule domainIsPureJava = noClasses().that().resideInAPackage("com.videos.domain..")
			.should().dependOnClassesThat().resideInAnyPackage(FRAMEWORKS)
			.because("the domain must not know Spring, JPA, Keycloak or any other framework");

	@ArchTest
	static final ArchRule domainDependsOnNothingElse = classes().that().resideInAPackage("com.videos.domain..")
			.should().onlyDependOnClassesThat().resideInAnyPackage("com.videos.domain..", "java..");

	@ArchTest
	static final ArchRule applicationDependsOnlyOnDomain = classes().that().resideInAPackage("com.videos.application..")
			.should().onlyDependOnClassesThat().resideInAnyPackage("com.videos.application..", "com.videos.domain..", "java..");

	@ArchTest
	static final ArchRule platformCallsStayInTheirAdapter = noClasses().that()
			.resideOutsideOfPackage("com.videos.infrastructure.platform..")
			.should().dependOnClassesThat().resideInAnyPackage("org.springframework.web.client..",
					"org.springframework.cloud.client..")
			.because("how users are looked up on the platform must only touch that adapter");

	@ArchTest
	static final ArchRule jpaStaysInPersistence = noClasses().that()
			.resideOutsideOfPackage("com.videos.infrastructure.persistence..")
			.should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.springframework.data..");

	@ArchTest
	static final ArchRule controllersCallUseCasesNotPorts = noClasses().that().resideInAPackage("com.videos.interfaces..")
			.should().dependOnClassesThat().resideInAPackage("com.videos.domain.port..")
			.because("the interfaces layer goes through application use cases");
}
