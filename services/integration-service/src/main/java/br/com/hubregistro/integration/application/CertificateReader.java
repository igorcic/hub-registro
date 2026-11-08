package br.com.hubregistro.integration.application;

import br.com.hubregistro.integration.domain.Certificate;

import java.util.UUID;

/** Lê um arquivo PKCS#12 ou PEM, extrai os metadados e devolve o conteúdo normalizado para guardar. */
public interface CertificateReader {

    ParsedCertificate read(UUID integrationId, String alias, Certificate.Purpose purpose, byte[] content, String password);

    record ParsedCertificate(Certificate certificate, byte[] keystoreBytes, String keystorePassword) {
    }
}
