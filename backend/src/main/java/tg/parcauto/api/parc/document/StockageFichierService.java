package tg.parcauto.api.parc.document;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.ResourceNotFoundException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Stockage local des justificatifs. Remplacable par un stockage objet (S3, MinIO) en production. */
@Slf4j
@Service
public class StockageFichierService {

    private static final Set<String> EXTENSIONS_AUTORISEES =
            Set.of("pdf", "jpg", "jpeg", "png", "webp", "doc", "docx", "xls", "xlsx");

    private final Path racine;

    public StockageFichierService(@Value("${parcauto.storage.documents-dir}") String dossier) throws IOException {
        this.racine = Paths.get(dossier).toAbsolutePath().normalize();
        Files.createDirectories(this.racine);
        log.info("Stockage des documents : {}", this.racine);
    }

    /** Enregistre le fichier et retourne son chemin relatif au dossier racine. */
    public String enregistrer(MultipartFile fichier, String sousDossier) {
        if (fichier == null || fichier.isEmpty()) {
            throw new BusinessException("Fichier vide");
        }
        String nomOriginal = Path.of(fichier.getOriginalFilename() == null ? "fichier"
                : fichier.getOriginalFilename()).getFileName().toString();
        String extension = extension(nomOriginal);
        if (!EXTENSIONS_AUTORISEES.contains(extension)) {
            throw new BusinessException("Extension de fichier non autorisee : " + extension);
        }
        try {
            Path dossier = racine.resolve(sousDossier).normalize();
            if (!dossier.startsWith(racine)) {
                throw new BusinessException("Chemin de stockage invalide");
            }
            Files.createDirectories(dossier);
            String nomStocke = UUID.randomUUID() + "." + extension;
            try (InputStream in = fichier.getInputStream()) {
                Files.copy(in, dossier.resolve(nomStocke), StandardCopyOption.REPLACE_EXISTING);
            }
            return racine.relativize(dossier.resolve(nomStocke)).toString().replace('\\', '/');
        } catch (IOException e) {
            throw new BusinessException("Impossible d'enregistrer le fichier : " + e.getMessage());
        }
    }

    public Resource charger(String cheminRelatif) {
        try {
            Path fichier = racine.resolve(cheminRelatif).normalize();
            if (!fichier.startsWith(racine)) {
                throw new BusinessException("Chemin de fichier invalide");
            }
            Resource resource = new UrlResource(fichier.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResourceNotFoundException("Fichier introuvable : " + cheminRelatif);
            }
            return resource;
        } catch (IOException e) {
            throw new ResourceNotFoundException("Fichier illisible : " + cheminRelatif);
        }
    }

    public void supprimer(String cheminRelatif) {
        if (cheminRelatif == null) {
            return;
        }
        try {
            Path fichier = racine.resolve(cheminRelatif).normalize();
            if (fichier.startsWith(racine)) {
                Files.deleteIfExists(fichier);
            }
        } catch (IOException e) {
            log.warn("Suppression du fichier {} impossible : {}", cheminRelatif, e.getMessage());
        }
    }

    private String extension(String nom) {
        int point = nom.lastIndexOf('.');
        return point < 0 ? "" : nom.substring(point + 1).toLowerCase(Locale.ROOT);
    }
}
