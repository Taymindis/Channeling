package com.github.taymindis.camel.helper;

import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class DefaultSSLContextParameters extends SSLContextParameters {

    public DefaultSSLContextParameters() throws KeyStoreException, NoSuchAlgorithmException {
        super();

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init((KeyStore) null);
        TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
        if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
            throw new IllegalStateException(
                    "Unexpected default trust managers:"
                                            + Arrays.toString(trustManagers));
        }
        TrustManager trustManager = (X509TrustManager) trustManagers[0];
        TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setTrustManager(trustManager);

        this.setTrustManagers(tmp);
    }

}
