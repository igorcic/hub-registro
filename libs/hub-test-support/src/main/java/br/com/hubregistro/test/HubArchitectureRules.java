package br.com.hubregistro.test;

import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Regras de Clean Architecture aplicadas a todo serviço:
 * {@code domain} ← {@code application} ← {@code infrastructure} / {@code web}.
 *
 * <p>{@code allowEmptyShould(true)} permite que um serviço ainda sem uma das camadas passe; a regra
 * passa a valer automaticamente quando a camada surgir.
 */
public final class HubArchitectureRules {

    private HubArchitectureRules() {
    }

    /** O domínio é Java puro: sem Spring, sem JPA, sem conhecer as camadas externas. */
    public static final ArchRule DOMAIN_IS_INDEPENDENT = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "org.hibernate..",
                    "..application..",
                    "..infrastructure..",
                    "..web..")
            .because("regras de negócio não podem depender de framework nem de adaptadores")
            .allowEmptyShould(true);

    /** Casos de uso dependem de portas, nunca dos adaptadores que as implementam. */
    public static final ArchRule APPLICATION_DOES_NOT_DEPEND_ON_ADAPTERS = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..", "..web..")
            .because("casos de uso falam com o mundo externo só por portas")
            .allowEmptyShould(true);

    /** Controllers traduzem HTTP em comandos; não acessam persistência diretamente. */
    public static final ArchRule WEB_DOES_NOT_ACCESS_PERSISTENCE = noClasses()
            .that().resideInAPackage("..web..")
            .should().dependOnClassesThat().resideInAnyPackage("..infrastructure.persistence..", "jakarta.persistence..")
            .because("controllers não têm regra de negócio nem acesso a dados")
            .allowEmptyShould(true);
}
