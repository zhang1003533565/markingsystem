package com.markingsystem.dto;

import lombok.Data;

@Data
public class MaterialChainUploadRequest {

    private Long applicationId;

    private String certNo;

    private String certName;
}
