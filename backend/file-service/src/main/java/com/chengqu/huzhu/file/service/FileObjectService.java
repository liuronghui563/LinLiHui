package com.chengqu.huzhu.file.service;

import com.chengqu.huzhu.common.exception.BizException;
import com.chengqu.huzhu.common.file.LocalFileUrls;
import com.chengqu.huzhu.common.security.SecurityUtils;
import com.chengqu.huzhu.common.security.UserPrincipal;
import com.chengqu.huzhu.file.config.MinioProperties;
import com.chengqu.huzhu.file.domain.FilePurpose;
import com.chengqu.huzhu.file.dto.FileUploadResponse;
import com.chengqu.huzhu.file.support.ImageMagic;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileObjectService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    /**
     * 允许被读取的对象键形状。
     *
     * <p>目录名**由 FilePurpose 推导**，不再手写：以前这里是一串硬编码的
     * {@code (avatar|post|aid|ad)}，加了新用途却忘了改这一行，上传会成功但
     * 读取时 404 —— 而且只在真的去读那张图时才暴露。现在新增一个用途，
     * 白名单会自动跟上。
     */
    private static final Pattern OBJECT_KEY = Pattern.compile(
            "^(" + Arrays.stream(FilePurpose.values())
                    .map(FilePurpose::folder)
                    .collect(Collectors.joining("|"))
                    + ")/\\d+/\\d{8}/[0-9a-fA-F-]{36}\\.(png|jpg|jpeg|webp)$");

    private final MinioClient minioClient;
    private final MinioProperties properties;

    @PostConstruct
    public void warmupBucket() {
        try {
            ensureBucket();
        } catch (Exception e) {
            log.warn("[文件] 启动时未能连上 MinIO（{}），将在首次上传时重试", e.getMessage());
        }
    }

    private void ensureBucket() {
        try {
            String bucket = properties.getBucket();
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("[文件] 已创建 MinIO bucket={}", bucket);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("对象存储暂不可用，请稍后重试");
        }
    }

    public FileUploadResponse upload(MultipartFile file, String purposeRaw) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的图片");
        }
        if (file.getSize() > ImageMagic.MAX_BYTES) {
            throw new BizException("图片不能超过 5MB");
        }
        UserPrincipal user = SecurityUtils.currentUser();
        FilePurpose purpose = FilePurpose.from(purposeRaw);
        if (purpose.requiresAdmin() && !isAdmin(user)) {
            throw new BizException(403, "仅管理员可上传广告图");
        }

        byte[] header = readHeader(file);
        ImageMagic.Detected detected = ImageMagic.detect(header, file.getOriginalFilename());
        String objectKey = purpose.folder() + "/" + user.getId() + "/"
                + LocalDate.now().format(DAY) + "/" + UUID.randomUUID() + "." + detected.extension();
        ensureBucket();

        try (InputStream input = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .stream(input, file.getSize(), -1)
                    .contentType(detected.contentType())
                    .build());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[文件] 上传失败 userId={}, purpose={}: {}", user.getId(), purpose, e.getMessage());
            throw new BizException("图片上传失败，请稍后重试");
        }

        String url = LocalFileUrls.PREFIX + objectKey;
        log.info("[文件] 上传成功 userId={}, purpose={}, key={}, size={}",
                user.getId(), purpose, objectKey, file.getSize());
        return FileUploadResponse.builder()
                .url(url)
                .objectKey(objectKey)
                .contentType(detected.contentType())
                .size(file.getSize())
                .build();
    }

    public ResponseEntity<StreamingResponseBody> stream(String objectKey) {
        String key = requireKey(objectKey);
        try {
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(key)
                    .build());
            GetObjectResponse object = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(key)
                    .build());
            String contentType = StringUtils.hasText(stat.contentType())
                    ? stat.contentType()
                    : MediaType.APPLICATION_OCTET_STREAM_VALUE;
            StreamingResponseBody body = output -> {
                try (object) {
                    object.transferTo(output);
                }
            };
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                    .contentType(MediaType.parseMediaType(contentType))
                    .contentLength(stat.size())
                    .body(body);
        } catch (Exception e) {
            throw new BizException(404, "文件不存在");
        }
    }

    public void delete(String objectKey) {
        String key = requireKey(objectKey);
        UserPrincipal user = SecurityUtils.currentUser();
        if (!isOwner(key, user) && !isAdmin(user)) {
            throw new BizException(403, "只能删除自己上传的文件");
        }
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(key)
                    .build());
            log.info("[文件] 删除成功 operatorId={}, key={}", user.getId(), key);
        } catch (Exception e) {
            log.warn("[文件] 删除失败 key={}: {}", key, e.getMessage());
            throw new BizException("删除失败，请稍后重试");
        }
    }

    private static byte[] readHeader(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            return input.readNBytes(16);
        } catch (Exception e) {
            throw new BizException("无法读取上传文件");
        }
    }

    private static String requireKey(String objectKey) {
        if (!StringUtils.hasText(objectKey) || objectKey.contains("..") || !OBJECT_KEY.matcher(objectKey).matches()) {
            throw new BizException(404, "文件不存在");
        }
        return objectKey;
    }

    private static boolean isOwner(String objectKey, UserPrincipal user) {
        String marker = "/" + user.getId() + "/";
        int slash = objectKey.indexOf('/');
        return slash > 0 && objectKey.startsWith(marker, slash);
    }

    private static boolean isAdmin(UserPrincipal user) {
        String role = user.getRole();
        if (role == null) {
            return false;
        }
        if (role.startsWith("ROLE_")) {
            role = role.substring(5);
        }
        return "ADMIN".equals(role);
    }
}
