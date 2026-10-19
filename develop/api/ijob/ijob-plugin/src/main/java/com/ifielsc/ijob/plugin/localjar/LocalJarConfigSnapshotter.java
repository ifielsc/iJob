package com.ifielsc.ijob.plugin.localjar;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.core.task.definition.TaskPluginConfigSnapshotter;
import com.ifielsc.ijob.plugin.common.config.PluginConfigCodec;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class LocalJarConfigSnapshotter implements TaskPluginConfigSnapshotter {
    private static final int MAX_CONFIG_BYTES = 256 * 1024;

    private final PluginConfigCodec configCodec;
    private final LocalJarArtifactValidator artifactValidator;

    @Override
    public PluginType supportType() {
        return PluginType.LOCAL_JAR;
    }

    @Override
    public String snapshot(String pluginConfig) {
        try {
            requireConfigSize(pluginConfig);
            LocalJarTaskConfig draft =
                    configCodec.read(pluginConfig, LocalJarTaskConfig.class, "Local JAR");
            if (draft == null) {
                throw new IllegalArgumentException("Local JAR configuration is required");
            }
            if (draft.getFormatVersion() != null
                    && draft.getFormatVersion() != LocalJarTaskConfig.FORMAT_VERSION) {
                throw new IllegalArgumentException("Local JAR format version is invalid");
            }
            LocalJarArtifact artifact =
                    artifactValidator.validate(
                            draft.getJarFile(), draft.getSha256(), draft.getEntryClass());
            LocalJarTaskConfig snapshot = new LocalJarTaskConfig();
            snapshot.setFormatVersion(LocalJarTaskConfig.FORMAT_VERSION);
            snapshot.setJarFile(artifact.fileName());
            snapshot.setJarSize(artifact.size());
            snapshot.setSha256(artifact.sha256());
            snapshot.setImplementationVersion(artifact.implementationVersion());
            snapshot.setSpiVersion(artifact.spiVersion());
            snapshot.setEntryClass(artifact.entryClass());
            snapshot.setUserConfig(draft.getUserConfig());
            String serialized = configCodec.write(snapshot, "Local JAR");
            requireConfigSize(serialized);
            return serialized;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw BusinessException.localized(
                    ResultCode.PARAM_ERROR, safeMessage(exception), exception);
        }
    }

    private void requireConfigSize(String value) {
        if (value == null
                || value.isBlank()
                || value.getBytes(StandardCharsets.UTF_8).length > MAX_CONFIG_BYTES) {
            throw new IllegalArgumentException("Local JAR configuration is invalid");
        }
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "Local JAR configuration is invalid"
                : message;
    }
}
