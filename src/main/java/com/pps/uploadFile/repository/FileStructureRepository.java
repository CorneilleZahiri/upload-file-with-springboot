package com.pps.uploadFile.repository;

import com.pps.uploadFile.entity.FileStructure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FileStructureRepository extends JpaRepository<FileStructure, UUID> {
}
