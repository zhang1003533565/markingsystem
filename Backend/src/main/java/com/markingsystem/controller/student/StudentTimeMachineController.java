package com.markingsystem.controller.student;

import com.markingsystem.dto.TimeMachineRequestDTO;
import com.markingsystem.dto.TimeMachineResultDTO;
import com.markingsystem.exception.RateLimitException;
import com.markingsystem.service.TimeMachineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * AI 时光机接口（学生端，仅限当前登录学生本人推演，接口本身不含任何写入）。
 */
@RestController
@RequestMapping("/api/student/time-machine")
public class StudentTimeMachineController {

    private static final Logger log = LoggerFactory.getLogger(StudentTimeMachineController.class);

    @Autowired
    private TimeMachineService timeMachineService;

    /**
     * 执行一次未来路径推演。
     * 入参假设参数全部可选，缺省 = 维持现状。
     */
    @PostMapping("/simulate")
    public ResponseEntity<Map<String, Object>> simulate(
            @RequestBody TimeMachineRequestDTO request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        Map<String, Object> result = new HashMap<>();

        try {
            Long parsedUserId = parseUserId(userId);
            Optional<TimeMachineResultDTO> simulation = timeMachineService.simulate(studentNo, parsedUserId, request);
            if (simulation.isEmpty()) {
                result.put("code", 404);
                result.put("message", "未找到当前学生或画像数据，请先在个人中心生成画像后再尝试");
                return ResponseEntity.badRequest().body(result);
            }
            result.put("code", 200);
            result.put("data", simulation.get());
            return ResponseEntity.ok(result);
        } catch (RateLimitException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                    "code", 429,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("学生时光机推演失败, userId={}, studentNo={}", userId, studentNo, e);
            result.put("code", 500);
            result.put("message", "时光机推演失败，请稍后重试");
            return ResponseEntity.internalServerError().body(result);
        }
    }

    private Long parseUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(userId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
