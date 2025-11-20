package com.pps.uploadFile.controller;

import com.pps.uploadFile.entity.FileStructure;
import com.pps.uploadFile.service.FileStructureService;
import lombok.AllArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/upload")
@AllArgsConstructor
public class FilesController {
    private final FileStructureService fileStructureService;

    @GetMapping("/list")
    public ResponseEntity<List<FileStructure>> fileList() {
        return ResponseEntity.ok(fileStructureService.fileStructureList());
    }

    @PostMapping("/save")
    public ResponseEntity<?> uploadAndSave(@RequestParam("file") MultipartFile[] files) {
        StringBuilder result = new StringBuilder();
        int compteur = 1;

        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("No file selected.");
            }

            result.append(compteur)
                    .append(" Enregistré avec succès : ")
                    .append(file.getOriginalFilename())
                    .append("\n");

            //Enregistré
            fileStructureService.saveFile(file);
            compteur++;
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> downloadFile(@PathVariable UUID id) {
        FileStructure fileStructure = fileStructureService.getFileById(id);
        Resource resource = fileStructureService.loadAsResource(id);

        String contentType = fileStructure.getType() != null ? fileStructure.getType() : "application/octet-stream";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileStructure.getOriginalFileName() + "\"")
                .body(resource);
    }

    @PutMapping("/{id}")
    public ResponseEntity<FileStructure> updateFile(@PathVariable UUID id, @RequestParam("file") MultipartFile newFile) {
        return ResponseEntity.ok(fileStructureService.updateFile(id, newFile));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        fileStructureService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
