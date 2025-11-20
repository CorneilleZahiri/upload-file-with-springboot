package com.pps.uploadFile.service;

import com.pps.uploadFile.config.FileStorageProperties;
import com.pps.uploadFile.entity.FileStructure;
import com.pps.uploadFile.exception.ErrorInterneServorException;
import com.pps.uploadFile.exception.FileNotFoundException;
import com.pps.uploadFile.repository.FileStructureRepository;
import lombok.AllArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@AllArgsConstructor
public class FileStructureService {

    private static final List<String> ALLOWED_EXTENSIONS = List.of("png", "jpg", "jpeg", "webp");
    private final FileStructureRepository fileStructureRepository;
    private final FileStorageProperties properties;

    @Transactional
    public void saveFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileNotFoundException("Fichier vide.");
        }

        // 3MB au maximum comme taille de fichier
        if (file.getSize() > 3 * 1024 * 1024) {
            throw new FileNotFoundException("Fichier trop volumineux.");
        }

        String originalFilename = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
        String ext = getExtension(file, originalFilename);

        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new FileNotFoundException("Extension non autorisée: " + ext);
        }

        Path uploadDir = Paths.get(properties.getUploadDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw new ErrorInterneServorException("Impossible de créer le répertoire d'upload.");
        }

        // 1- D'abord, on crée l'entité SANS chemin
        FileStructure entity = new FileStructure();
        entity.setOriginalFileName(originalFilename);
        entity.setType(file.getContentType());

        // on force l'insertion pour obtenir l'id
        entity = fileStructureRepository.saveAndFlush(entity);

        // 2- Utilise l'id généré comme nom définitif
        UUID id = entity.getId();
        String filename = id + "." + ext;
        Path finalPath = uploadDir.resolve(filename);

        if (!finalPath.startsWith(uploadDir)) {
            throw new SecurityException("Chemin de stockage invalide.");
        }

        // 3- Mettre à jour uniquement le nom du fichier comme chemin et reconstruire le chemin complet après
        entity.setFilePath(filename);
        fileStructureRepository.save(entity);

        // 4- Écriture post-commit
        registerFileWriteAfterCommit(file, finalPath, uploadDir);
    }

    @Transactional
    public FileStructure getFileById(UUID id) {
        return fileStructureRepository.findById(id)
                .orElseThrow(() -> new FileNotFoundException("Fichier introuvable"));
    }

    @Transactional
    public List<FileStructure> fileStructureList() {
        return fileStructureRepository.findAll().stream().toList();
    }

    @Transactional
    public Resource loadAsResource(UUID id) {
        FileStructure fileStructure = getFileById(id);
        //Reconstruire le chemin du fichier à partir de file_path en base de données
        Path physicalPath = resolvePhysicalPath(fileStructure.getFilePath());
        try {
            Resource resource = new UrlResource(physicalPath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new FileNotFoundException("Fichier non accessible.");
            }
        } catch (MalformedURLException e) {
            throw new ErrorInterneServorException("Erreur lors de l'accès au fichier.");
        }
    }

    @Transactional
    public void deleteById(UUID uuid) {
        //Rechercher l'entité
        FileStructure fileStructure = getFileById(uuid);

        //Reconstruire le chemin du fichier à partir de file_path en base de données
        Path physicalPath = resolvePhysicalPath(fileStructure.getFilePath());

        //Supprimer l'entité dans la base de données
        fileStructureRepository.delete(fileStructure);

        // Exécution après commit DB
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        //Appel à une autre méthode pour la suppression du fichier sur le disque
                        deletePhysicalFile(physicalPath);
                    }
                }
        );
    }

    @Transactional
    public FileStructure updateFile(UUID id, MultipartFile newFile) {
        FileStructure fileEntity = getFileById(id);

        Path oldPath = resolvePhysicalPath(fileEntity.getFilePath());
        Path uploadDir = Paths.get(properties.getUploadDir()).toAbsolutePath().normalize();

        String originalName = StringUtils.cleanPath(Objects.requireNonNull(newFile.getOriginalFilename()));
        String ext = getExtension(newFile, originalName); // méthode utilitaire

        // 1) Persister d'abord en base
        fileEntity.setOriginalFileName(originalName);
        fileEntity.setType(newFile.getContentType());
        fileEntity.setFilePath(id.toString() + "." + ext);
        fileEntity = fileStructureRepository.save(fileEntity);

        // 4- Écriture post-commit
        registerFileWriteAfterCommit(newFile, oldPath, uploadDir);

        return fileEntity;
    }

    private void deletePhysicalFile(Path path) {
        if (path == null) return;

        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // Log + gestion d’erreur métier
            throw new FileNotFoundException("La suppression du fichier a échoué" +
                    " malgré la suppression en base. Path: " + path);
        }
    }

    private String getExtension(MultipartFile file, String originalFilename) {
        return Optional.ofNullable(StringUtils.getFilenameExtension(originalFilename))
                .orElseGet(() -> guessExtensionFromContentType(file.getContentType()).orElse("bin"))
                .toLowerCase();
    }

    private void registerFileWriteAfterCommit(MultipartFile file, Path finalPath, Path uploadDir) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    //Crée toute la hiérarchie de dossiers, même s'il y a plusieurs niveaux.
                    //Ne génère pas d’erreur si le dossier existe (idempotent).
                    Files.createDirectories(uploadDir);

                    try (InputStream in = file.getInputStream()) { //retourne le courant binaire du fichier uploadé
                        //copie le flux (in) vers finalPath
                        //crée le fichier physiquement
                        //REPLACE_EXISTING permet d'écraser un fichier existant portant le même nom
                        Files.copy(in, finalPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new ErrorInterneServorException("Stockage fichier échoué après commit.");
                }
            }
        });
    }

    // Méthode pour réconstruire le chemin du fichier
    private Path resolvePhysicalPath(String storedPath) {
        return Paths.get(properties.getUploadDir())
                .resolve(storedPath)
                .toAbsolutePath()
                .normalize();
    }

    private Optional<String> guessExtensionFromContentType(String contentType) {
        if (contentType == null) return Optional.empty();
        return switch (contentType) {
            case "image/png" -> Optional.of("png");
            case "image/jpeg" -> Optional.of("jpg");
            case "application/pdf" -> Optional.of("pdf");
            default -> Optional.empty();
        };
    }
}
