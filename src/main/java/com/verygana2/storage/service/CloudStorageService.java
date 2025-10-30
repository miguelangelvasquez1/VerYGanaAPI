package com.verygana2.storage.service;

import org.springframework.web.multipart.MultipartFile;

import com.verygana2.storage.dto.FileMetadata;
import com.verygana2.storage.dto.UploadOptions;
import com.verygana2.storage.dto.UploadResult;
import com.verygana2.storage.dto.UrlOptions;

import java.util.List;

/**
 * Interfaz genérica para servicios de almacenamiento en la nube.
 * Permite cambiar fácilmente entre providers (Cloudinary, R2, S3, etc.)
 */
public interface CloudStorageService {

    /**
     * Sube un archivo al almacenamiento
     * 
     * @param file Archivo a subir
     * @param folder Carpeta destino (ej: "ads/123")
     * @param options Opciones adicionales (transformaciones, metadata, etc.)
     * @return Resultado del upload con URL y metadata
     */
    UploadResult uploadFile(MultipartFile file, String folder, UploadOptions options);

    /**
     * Elimina un archivo del almacenamiento
     * 
     * @param publicId ID público del archivo
     * @return true si se eliminó exitosamente
     */
    boolean deleteFile(String publicId);

    /**
     * Genera una URL pública para un archivo
     * 
     * @param publicId ID público del archivo
     * @param options Opciones de transformación (tamaño, formato, calidad)
     * @return URL pública del archivo
     */
    String generateUrl(String publicId, UrlOptions options);

    /**
     * Genera una URL firmada temporal
     * 
     * @param publicId ID público del archivo
     * @param expirationMinutes Minutos hasta expiración
     * @return URL firmada
     */
    String generateSignedUrl(String publicId, int expirationMinutes);

    /**
     * Lista archivos en una carpeta
     * 
     * @param folder Carpeta a listar
     * @return Lista de IDs de archivos
     */
    List<String> listFiles(String folder);

    /**
     * Obtiene información de un archivo
     * 
     * @param publicId ID público del archivo
     * @return Metadata del archivo
     */
    FileMetadata getFileMetadata(String publicId);

    /**
     * Valida si el servicio está disponible
     * 
     * @return true si el servicio está operativo
     */
    boolean isHealthy();
}