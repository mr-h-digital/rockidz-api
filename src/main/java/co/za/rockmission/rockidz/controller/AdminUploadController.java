package co.za.rockmission.rockidz.controller;

import co.za.rockmission.rockidz.dto.FileUploadResponse;
import co.za.rockmission.rockidz.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/uploads")
@RequiredArgsConstructor
public class AdminUploadController {

    private final StorageService storageService;

    @PostMapping("/downloadables")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDUCATOR')")
    public ResponseEntity<FileUploadResponse> uploadDownloadable(@RequestParam("file") MultipartFile file) {
        StorageService.UploadedFile uploaded = storageService.uploadDownloadable(file);
        return ResponseEntity.ok(new FileUploadResponse(uploaded.url(), uploaded.key(), uploaded.contentType()));
    }
}
