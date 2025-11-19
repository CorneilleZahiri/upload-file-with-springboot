package com.pps.uploadFile.controller;

import com.pps.uploadFile.service.FilesService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/upload")
@AllArgsConstructor
public class FilesController {
    private final FilesService filesService;

    @PostMapping("/save")
    public ResponseEntity<?> uploadAndSave(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("No file selected.");
        }

        return ResponseEntity.ok(filesService.saveFile(file));
    }
}
