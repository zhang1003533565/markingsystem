package com.markingsystem.service;

import java.util.List;

public interface JobLearningResourceClient {

    String source();

    String modelName();

    record GeneratedLearningResource(String title, String description, String guide, String link, String content) {}

    List<GeneratedLearningResource> generateResources(String jobTitle) throws Exception;
}