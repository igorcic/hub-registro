package br.com.hubregistro.integration;

import br.com.hubregistro.test.HubArchitectureRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "br.com.hubregistro.integration", importOptions = ImportOption.DoNotIncludeTests.class)
class IntegrationArchitectureTest {

    @ArchTest
    static final ArchRule domainIsIndependent = HubArchitectureRules.DOMAIN_IS_INDEPENDENT;

    @ArchTest
    static final ArchRule applicationDoesNotDependOnAdapters = HubArchitectureRules.APPLICATION_DOES_NOT_DEPEND_ON_ADAPTERS;

    @ArchTest
    static final ArchRule webDoesNotAccessPersistence = HubArchitectureRules.WEB_DOES_NOT_ACCESS_PERSISTENCE;
}
