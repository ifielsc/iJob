package com.ifielsc.ijob.api.config;

import cn.dev33.satoken.config.SaTokenConfig;

import com.ifielsc.ijob.common.cluster.ClusterStateStore;
import com.ifielsc.ijob.common.config.AppProperties;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
@DependsOnDatabaseInitialization
@ConditionalOnProperty(prefix = "app.runtime", name = "mode", havingValue = "CLUSTER")
public class ClusterCompatibilityGuard implements InitializingBean {
    static final String COMPATIBILITY_KEY = "ijob:cluster:compatibility:v1";

    private final ClusterStateStore stateStore;
    private final AppProperties appProperties;
    private final SaTokenConfig saTokenConfig;
    private final boolean flywayEnabled;

    @Autowired
    public ClusterCompatibilityGuard(
            ClusterStateStore stateStore,
            AppProperties appProperties,
            SaTokenConfig saTokenConfig,
            @Value("${spring.flyway.enabled:false}") boolean flywayEnabled) {
        this.stateStore = stateStore;
        this.appProperties = appProperties;
        this.saTokenConfig = saTokenConfig;
        this.flywayEnabled = flywayEnabled;
    }

    ClusterCompatibilityGuard(
            ClusterStateStore stateStore,
            AppProperties appProperties,
            SaTokenConfig saTokenConfig) {
        this(stateStore, appProperties, saTokenConfig, true);
    }

    @Override
    public void afterPropertiesSet() {
        if (!flywayEnabled) {
            throw new IllegalStateException("Cluster mode requires Flyway validation");
        }
        String localFingerprint = fingerprint();
        String clusterFingerprint =
                stateStore.initializeOrRead(COMPATIBILITY_KEY, localFingerprint);
        if (!localFingerprint.equals(clusterFingerprint)) {
            throw new IllegalStateException(
                    "Cluster authentication or encryption configuration is incompatible");
        }
    }

    String fingerprint() {
        AppProperties.Auth auth = appProperties.getAuth();
        AppProperties.Security.Aes aes = appProperties.getSecurity().getAes();
        String material =
                String.join(
                        "\u0000",
                        "ijob-cluster-protocol-v1",
                        value(auth.getUsername()),
                        value(auth.getPasswordHash()),
                        value(aes.getKeyId()),
                        value(aes.getKey()),
                        value(saTokenConfig.getTokenName()),
                        Long.toString(saTokenConfig.getTimeout()),
                        Long.toString(saTokenConfig.getActiveTimeout()),
                        value(saTokenConfig.getIsConcurrent()),
                        value(saTokenConfig.getIsShare()),
                        value(saTokenConfig.getTokenStyle()),
                        value(saTokenConfig.getIsReadHeader()),
                        value(saTokenConfig.getIsReadCookie()));
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String value(Object value) {
        return value == null ? "<null>" : value.toString();
    }
}
