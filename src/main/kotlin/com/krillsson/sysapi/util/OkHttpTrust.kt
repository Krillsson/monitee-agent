package com.krillsson.sysapi.util

import okhttp3.OkHttpClient
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

fun OkHttpClient.Builder.trustingEveryCertificate(): OkHttpClient.Builder {
    val trustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
    return trustingOnly(trustManager).hostnameVerifier { _, _ -> true }
}

fun OkHttpClient.Builder.trustingCertificatesIn(pemFile: File): OkHttpClient.Builder {
    val certificates = pemFile.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificates(it) }
    require(certificates.isNotEmpty()) { "${pemFile.path} holds no certificates" }
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
        load(null, null)
        certificates.forEachIndexed { index, certificate -> setCertificateEntry("ca-$index", certificate) }
    }
    val trustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        .apply { init(keyStore) }
        .trustManagers
        .filterIsInstance<X509TrustManager>()
        .first()
    return trustingOnly(trustManager)
}

private fun OkHttpClient.Builder.trustingOnly(trustManager: X509TrustManager): OkHttpClient.Builder {
    val sslContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
    }
    return sslSocketFactory(sslContext.socketFactory, trustManager)
}
