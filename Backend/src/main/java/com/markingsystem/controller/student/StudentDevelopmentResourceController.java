package com.markingsystem.controller.student;

import com.markingsystem.dto.DevelopmentResourceDTO;
import com.markingsystem.service.DevelopmentResourceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 学生端 - 发展助手资源推荐接口
 */
@RestController
@RequestMapping("/api/student/development-resources")
public class StudentDevelopmentResourceController {

    private final DevelopmentResourceService developmentResourceService;

    public StudentDevelopmentResourceController(DevelopmentResourceService developmentResourceService) {
        this.developmentResourceService = developmentResourceService;
    }

    /**
     * 获取架构工程师方向的学习资源推荐
     */
    @GetMapping("/architecture")
    public ResponseEntity<Map<String, Object>> listArchitectureResources() {
        try {
            List<DevelopmentResourceDTO> resources = developmentResourceService.listArchitectureResources();
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "data", resources
            ));
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "code", 500,
                    "message", "获取学习资源推荐失败：" + exception.getMessage()
            ));
        }
    }
}
