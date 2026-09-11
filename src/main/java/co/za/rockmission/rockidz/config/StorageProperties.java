package co.za.rockmission.rockidz.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        String endpointUrl,
        String region,
        String bucketName,
        String accessKeyId,
        String secretAccessKey,
        String publicBaseUrl) {
}
