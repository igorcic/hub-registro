package br.com.hubregistro.auth.application;

/** Resultado de login ou renovação. */
public record TokenPair(String accessToken, long accessTokenExpiresIn, String refreshToken, long refreshTokenExpiresIn) {
}
