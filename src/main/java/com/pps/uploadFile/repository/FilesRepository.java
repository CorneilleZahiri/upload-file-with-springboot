package com.pps.uploadFile.repository;

import com.pps.uploadFile.entity.FileUpload;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FilesRepository extends JpaRepository<FileUpload, UUID> {
}
