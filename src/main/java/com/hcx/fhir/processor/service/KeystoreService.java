package com.hcx.fhir.processor.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;

// HDC-175: Downloads the SureScripts P12 keystore from S3 and builds an mTLS SSLContext.
@Slf4j
@Service
@RequiredArgsConstructor
public class KeystoreService {

    private final S3Client s3Client;

    // HDC-175: Downloads the P12 keystore bytes from S3.
    public byte[] downloadKeystore(String bucket, String key) {
        log.debug("HDC-175: Downloading keystore from s3://{}/{}", bucket, key);
        try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(
                GetObjectRequest.builder().bucket(bucket).key(key).build())) {
            byte[] bytes = response.readAllBytes();
            log.debug("HDC-175: Downloaded keystore bytes={}", bytes.length);
            return bytes;
        } catch (Exception e) {
            log.error("HDC-175: Failed to download keystore from s3://{}/{}", bucket, key, e);
            throw new RuntimeException("HDC-175: Failed to download keystore", e);
        }
    }

    // HDC-175: Loads the PKCS12 keystore and builds an SSLContext for mTLS.
    // The SSLContext uses the client certificate for mutual authentication with SureScripts.
    @Deprecated // HDC-214: Use buildSslContext(byte[], String, byte[]) instead.
    public SSLContext buildSslContext(byte[] p12Bytes, String keystorePassword) {
        return buildSslContext(p12Bytes, keystorePassword, null);
    }

    // HDC-214: Builds an mTLS SSLContext, optionally merging extra CA certs (e.g. a .p7b bundle)
    // into the JVM's default truststore so non-publicly-trusted staging servers can be validated.
    public SSLContext buildSslContext(byte[] p12Bytes, String keystorePassword, @Nullable byte[] extraCaBytes) {
        log.debug("HDC-175: Building mTLS SSLContext from P12 keystore");
        try {
            char[] password = keystorePassword.toCharArray();

            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(new ByteArrayInputStream(p12Bytes), password);

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, password);

            // HDC-214: Build a merged trust store combining JVM defaults with any extra CA bundle.
            KeyStore trustStore = buildMergedTrustStore(extraCaBytes);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            log.debug("HDC-175: mTLS SSLContext built successfully");
            return sslContext;
        } catch (Exception e) {
            log.error("HDC-175: Failed to build mTLS SSLContext", e);
            throw new RuntimeException("HDC-175: Failed to build SSLContext", e);
        }
    }

    // HDC-214: Builds a KeyStore containing the JVM's default CA trust anchors plus any
    // additional certificates parsed from extraCaBytes (supports PEM and PKCS#7 / .p7b formats).
    KeyStore buildMergedTrustStore(@Nullable byte[] extraCaBytes) throws Exception {
        // Start with the JVM default trust anchors
        TrustManagerFactory defaultTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        defaultTmf.init((KeyStore) null);
        X509TrustManager defaultTm = (X509TrustManager) defaultTmf.getTrustManagers()[0];

        KeyStore merged = KeyStore.getInstance(KeyStore.getDefaultType());
        merged.load(null, null);

        for (X509Certificate cert : defaultTm.getAcceptedIssuers()) {
            merged.setCertificateEntry(cert.getSubjectX500Principal().getName(), cert);
        }
        log.debug("HDC-214: Loaded {} default JVM trust anchors", defaultTm.getAcceptedIssuers().length);

        if (extraCaBytes != null) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Collection<?> extraCerts = cf.generateCertificates(new ByteArrayInputStream(extraCaBytes));
            int i = 0;
            for (Object cert : extraCerts) {
                merged.setCertificateEntry("extra-ca-" + i++, (java.security.cert.Certificate) cert);
            }
            log.info("HDC-214: Merged {} extra CA certificate(s) into truststore", extraCerts.size());
        }

        return merged;
    }
}
