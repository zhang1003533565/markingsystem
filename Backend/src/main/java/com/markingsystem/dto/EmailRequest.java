package com.markingsystem.dto;

import lombok.Data;

@Data
public class EmailRequest {
    private String toEmail;
    private String title;
    private String linkText;
    private String linkUrl;
    private String senderEmail;
    private String senderType;
}
