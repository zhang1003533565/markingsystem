package com.markingsystem.dto;

import lombok.Data;
import java.util.List;

@Data
public class ImportResultDTO {
    
    private int totalCount;
    private int successCount;
    private int failCount;
    private List<String> errorMessages;
    
    public ImportResultDTO(int totalCount, int successCount, List<String> errorMessages) {
        this.totalCount = totalCount;
        this.successCount = successCount;
        this.failCount = totalCount - successCount;
        this.errorMessages = errorMessages;
    }
}
