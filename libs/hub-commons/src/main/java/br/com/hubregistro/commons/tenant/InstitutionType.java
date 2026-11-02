package br.com.hubregistro.commons.tenant;

/** Tipo da instituição dona de um usuário ou credencial; define o portal e o escopo de dados. */
public enum InstitutionType {
    /** Operadora da plataforma: visão global. */
    HUB,
    /** Instituição financeira: vê só os próprios contratos. */
    BANK,
    /** Empresa registradora: vê só os contratos encaminhados a ela. */
    REGISTRY
}
