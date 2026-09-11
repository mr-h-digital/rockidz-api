package co.za.rockmission.rockidz.dto;

public record FileUploadResponse(
        String url,
        String storageKey,
        String contentType) {
}
