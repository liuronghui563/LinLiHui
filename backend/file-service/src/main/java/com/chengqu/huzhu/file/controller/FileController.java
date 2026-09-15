package com.chengqu.huzhu.file.controller;

import com.chengqu.huzhu.common.api.ApiResponse;
import com.chengqu.huzhu.file.dto.FileUploadResponse;
import com.chengqu.huzhu.file.service.FileObjectService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {

    private final FileObjectService fileObjectService;

    @PostMapping("/upload")
    public ApiResponse<FileUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("purpose") String purpose) {
        return ApiResponse.ok("上传成功", fileObjectService.upload(file, purpose));
    }

    @GetMapping("/objects/**")
    public ResponseEntity<StreamingResponseBody> get(HttpServletRequest request) {
        return fileObjectService.stream(extractObjectKey(request));
    }

    @DeleteMapping("/objects/**")
    public ApiResponse<Void> delete(HttpServletRequest request) {
        fileObjectService.delete(extractObjectKey(request));
        return ApiResponse.okMessage("已删除");
    }

    private static String extractObjectKey(HttpServletRequest request) {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String prefix = pattern == null ? "/api/file/objects/" : pattern.replace("/**", "/");
        if (path == null || !path.startsWith(prefix)) {
            return "";
        }
        return URLDecoder.decode(path.substring(prefix.length()), StandardCharsets.UTF_8);
    }
}
