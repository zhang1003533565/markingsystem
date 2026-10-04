package com.markingsystem.dto;

import lombok.Data;
import java.util.List;

@Data
public class BatchEmailRequest {
    private List<String> toEmails;
    private String title;
    private String content;
    private String linkText;
    private String linkUrl;
    private String senderEmail;
    private String senderType;
}
