package co.za.rockmission.rockidz.storage;

import co.za.rockmission.rockidz.config.StorageProperties;
import co.za.rockmission.rockidz.exception.ApiException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@ConditionalOnProperty(prefix = "app.storage", name = "endpoint-url")
public class StorageService {

    private static final int MAX_ACTIVITY_IMAGE_DIMENSION = 1600;
    private static final float ACTIVITY_IMAGE_QUALITY = 0.82f;

    private final StorageProperties storageProperties;
    private volatile S3Client s3Client;

    public StorageService(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    public UploadedFile uploadAvatar(MultipartFile file, Long userId) {
        return upload(file, "avatars/" + userId + "/", true, false);
    }

    public UploadedFile uploadDownloadable(MultipartFile file) {
        return upload(file, "downloads/", false, true);
    }

    public UploadedFile uploadCourseThumbnail(MultipartFile file, Long courseId) {
        ensureConfigured();

        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please choose an image to upload.");
        }

        String contentType = normalizeContentType(file.getContentType());
        if (!contentType.startsWith("image/")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only image files can be uploaded here.");
        }

        OptimizedImage optimizedImage = optimizeActivityImage(file);
        String objectKey = "course-thumbnails/" + courseId + "/" + UUID.randomUUID() + ".webp";

        getS3Client().putObject(
                PutObjectRequest.builder()
                        .bucket(requireConfigured(storageProperties.bucketName(), "STORAGE_BUCKET_NAME"))
                        .key(objectKey)
                        .contentType(optimizedImage.contentType())
                        .acl(ObjectCannedACL.PUBLIC_READ)
                        .build(),
                RequestBody.fromBytes(optimizedImage.bytes()));

        return new UploadedFile(buildPublicUrl(objectKey), objectKey, optimizedImage.contentType());
    }

    private UploadedFile upload(MultipartFile file, String prefix, boolean requireImage, boolean allowDocuments) {
        ensureConfigured();

        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please choose a file to upload.");
        }

        String contentType = normalizeContentType(file.getContentType());
        if (requireImage && !contentType.startsWith("image/")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only image files can be uploaded here.");
        }
        if (!requireImage && !isAllowedDownloadableContentType(contentType, allowDocuments)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only approved image, PDF, audio, or document files can be uploaded.");
        }

        String objectKey = prefix + UUID.randomUUID() + resolveExtension(file.getOriginalFilename(), contentType);

        try {
            getS3Client().putObject(
                    PutObjectRequest.builder()
                            .bucket(requireConfigured(storageProperties.bucketName(), "STORAGE_BUCKET_NAME"))
                            .key(objectKey)
                            .contentType(contentType)
                            .acl(ObjectCannedACL.PUBLIC_READ)
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unable to read the uploaded file.");
        }

        return new UploadedFile(buildPublicUrl(objectKey), objectKey, contentType);
    }

    private OptimizedImage optimizeActivityImage(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            BufferedImage source = ImageIO.read(inputStream);
            if (source == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported image format.");
            }

            BufferedImage resized = Thumbnails.of(source)
                    .size(MAX_ACTIVITY_IMAGE_DIMENSION, MAX_ACTIVITY_IMAGE_DIMENSION)
                    .keepAspectRatio(true)
                    .asBufferedImage();

            writeWebp(resized, outputStream);
            return new OptimizedImage(outputStream.toByteArray(), "image/webp");
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unable to process the uploaded image.");
        }
    }

    private void writeWebp(BufferedImage image, ByteArrayOutputStream outputStream) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType("image/webp");
        if (!writers.hasNext()) {
            throw new IllegalStateException("WebP writer is not available.");
        }

        ImageWriter writer = writers.next();
        try (ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(outputStream)) {
            writer.setOutput(imageOutputStream);

            ImageWriteParam writeParam = writer.getDefaultWriteParam();
            if (writeParam.canWriteCompressed()) {
                writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                String[] compressionTypes = writeParam.getCompressionTypes();
                if (compressionTypes != null && compressionTypes.length > 0) {
                    writeParam.setCompressionType(compressionTypes[0]);
                }
                writeParam.setCompressionQuality(ACTIVITY_IMAGE_QUALITY);
            }

            writer.write(null, new IIOImage(image, null, null), writeParam);
        } finally {
            writer.dispose();
        }
    }

    private void ensureConfigured() {
        requireConfigured(storageProperties.endpointUrl(), "STORAGE_ENDPOINT_URL");
        requireConfigured(storageProperties.region(), "STORAGE_REGION");
        requireConfigured(storageProperties.bucketName(), "STORAGE_BUCKET_NAME");
        requireConfigured(storageProperties.accessKeyId(), "STORAGE_ACCESS_KEY_ID");
        requireConfigured(storageProperties.secretAccessKey(), "STORAGE_SECRET_ACCESS_KEY");
    }

    private S3Client getS3Client() {
        S3Client client = s3Client;
        if (client != null) {
            return client;
        }

        synchronized (this) {
            if (s3Client == null) {
                s3Client = buildClient(storageProperties);
            }
            return s3Client;
        }
    }

    private S3Client buildClient(StorageProperties properties) {
        String endpointUrl = requireConfigured(properties.endpointUrl(), "STORAGE_ENDPOINT_URL");
        String region = requireConfigured(properties.region(), "STORAGE_REGION");
        String accessKey = requireConfigured(properties.accessKeyId(), "STORAGE_ACCESS_KEY_ID");
        String secretKey = requireConfigured(properties.secretAccessKey(), "STORAGE_SECRET_ACCESS_KEY");

        return S3Client.builder()
                .endpointOverride(URI.create(endpointUrl))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(true)
                .build();
    }

    private String normalizeContentType(String value) {
        if (value == null || value.isBlank()) {
            return "application/octet-stream";
        }
        return value.toLowerCase(Locale.ROOT).trim();
    }

    private boolean isAllowedDownloadableContentType(String contentType, boolean allowDocuments) {
        if (contentType.startsWith("image/")) return true;
        if (contentType.startsWith("audio/")) return true;
        if ("application/pdf".equals(contentType)) return true;
        if (!allowDocuments) return false;
        return "application/msword".equals(contentType)
                || "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(contentType)
                || "application/vnd.ms-powerpoint".equals(contentType)
                || "application/vnd.openxmlformats-officedocument.presentationml.presentation".equals(contentType);
    }

    private String resolveExtension(String originalFilename, String contentType) {
        if (contentType.contains("jpeg") || contentType.contains("jpg")) return ".jpg";
        if (contentType.contains("png")) return ".png";
        if (contentType.contains("webp")) return ".webp";
        if (contentType.contains("gif")) return ".gif";
        if (contentType.contains("svg")) return ".svg";
        if (contentType.contains("pdf")) return ".pdf";
        if (contentType.contains("mpeg")) return ".mp3";
        if (contentType.contains("wav")) return ".wav";
        if (contentType.contains("ogg")) return ".ogg";
        if (originalFilename != null && originalFilename.contains(".")) {
            String extension = originalFilename.substring(originalFilename.lastIndexOf('.'));
            if (extension.length() <= 10) {
                return extension.toLowerCase(Locale.ROOT);
            }
        }
        return ".bin";
    }

    private String buildPublicUrl(String objectKey) {
        String publicBaseUrl = storageProperties.publicBaseUrl();
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            return joinUrl(normalizeBaseUrl(publicBaseUrl), objectKey);
        }

        return joinUrl(
                requireConfigured(storageProperties.endpointUrl(), "STORAGE_ENDPOINT_URL"),
                requireConfigured(storageProperties.bucketName(), "STORAGE_BUCKET_NAME"),
                objectKey
        );
    }

    private String normalizeBaseUrl(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        return "https://" + trimmed;
    }

    private String joinUrl(String... parts) {
        StringBuilder url = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part == null || part.isBlank()) continue;
            String normalized = part.trim();
            if (i == 0) {
                normalized = normalized.replaceAll("/+$", "");
            } else {
                normalized = normalized.replaceAll("^/+|/+$", "");
            }
            if (!url.isEmpty() && url.charAt(url.length() - 1) != '/') {
                url.append('/');
            }
            url.append(normalized);
        }
        return url.toString();
    }

    private String requireConfigured(String value, String envName) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    envName + " must be configured for file uploads.");
        }
        return value.trim();
    }

    public record UploadedFile(String url, String key, String contentType) {
    }

    private record OptimizedImage(byte[] bytes, String contentType) {
    }
}
