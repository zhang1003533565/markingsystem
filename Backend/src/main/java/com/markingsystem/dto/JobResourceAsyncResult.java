package com.markingsystem.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 岗位学习资源异步生成结果。
 * status:
 * - ready：已有可用资源
 * - generating：后台生成中（前端应轮询）
 * - empty：无缓存且未触发生成
 * - failed：最近一次生成失败
 */
public class JobResourceAsyncResult {

    public static final String STATUS_READY = "ready";
    public static final String STATUS_GENERATING = "generating";
    public static final String STATUS_EMPTY = "empty";
    public static final String STATUS_FAILED = "failed";

    private String status;
    private List<JobLearningResourceDTO> data = new ArrayList<>();
    private String message;

    public static JobResourceAsyncResult ready(List<JobLearningResourceDTO> data) {
        JobResourceAsyncResult result = new JobResourceAsyncResult();
        result.status = STATUS_READY;
        result.data = data != null ? data : new ArrayList<>();
        return result;
    }

    public static JobResourceAsyncResult generating(List<JobLearningResourceDTO> data, String message) {
        JobResourceAsyncResult result = new JobResourceAsyncResult();
        result.status = STATUS_GENERATING;
        result.data = data != null ? data : new ArrayList<>();
        result.message = message;
        return result;
    }

    public static JobResourceAsyncResult empty() {
        JobResourceAsyncResult result = new JobResourceAsyncResult();
        result.status = STATUS_EMPTY;
        result.data = new ArrayList<>();
        return result;
    }

    public static JobResourceAsyncResult failed(List<JobLearningResourceDTO> data, String message) {
        JobResourceAsyncResult result = new JobResourceAsyncResult();
        result.status = STATUS_FAILED;
        result.data = data != null ? data : new ArrayList<>();
        result.message = message;
        return result;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<JobLearningResourceDTO> getData() {
        return data;
    }

    public void setData(List<JobLearningResourceDTO> data) {
        this.data = data;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
