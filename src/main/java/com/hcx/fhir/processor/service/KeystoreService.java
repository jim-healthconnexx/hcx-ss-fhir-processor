package com.hcx.fhir.processor.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.security.KeyStore;

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
    public SSLContext buildSslContext(byte[] p12Bytes, String keystorePassword) {
        log.debug("HDC-175: Building mTLS SSLContext from P12 keystore");
        try {
            char[] password = keystorePassword.toCharArray();

            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(new ByteArrayInputStream(p12Bytes), password);

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, password);

            // HDC-175: Use default trust store — Surescripts uses a publicly trusted server cert.
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            log.debug("HDC-175: mTLS SSLContext built successfully");
            return sslContext;
        } catch (Exception e) {
            log.error("HDC-175: Failed to build mTLS SSLContext", e);
            throw new RuntimeException("HDC-175: Failed to build SSLContext", e);
        }
    }
}
