package com.chengqu.huzhu.file.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FileUploadResponse {

    private String url;
    private String objectKey;
    private String contentType;
    private long size;
}
