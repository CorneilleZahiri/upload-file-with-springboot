package com.pps.uploadFile.service;

import com.pps.uploadFile.entity.FileUpload;
import com.pps.uploadFile.exception.ErrorInterneServorException;
import com.pps.uploadFile.repository.FilesRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@AllArgsConstructor
@Builder
public class FilesService {
    private final FilesRepository filesRepository;

    @Transactional
    public FileUpload saveFile(MultipartFile file) {
        try {
            String uploadDir = "uploads/";

            File directory = new File(uploadDir);
            if (!directory.exists()) {
                directory.mkdir();
            }

            Path path = Paths.get(uploadDir + file.getOriginalFilename());
            Files.write(path, file.getBytes());

            //Mapping
            FileUpload fileUpload = new FileUpload();
            fileUpload.setOriginalFileName(file.getOriginalFilename());
            fileUpload.setFilePath(path.toAbsolutePath().toString());
            fileUpload.setType(file.getContentType());

            return filesRepository.save(fileUpload);

        } catch (IOException e) {
            throw new ErrorInterneServorException("Erreur interne");
        }
    }
}
