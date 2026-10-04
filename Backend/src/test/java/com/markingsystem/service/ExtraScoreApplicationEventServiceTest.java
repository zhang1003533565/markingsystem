package com.markingsystem.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.markingsystem.entity.ExtraScoreApplication;
import com.markingsystem.entity.ExtraScoreApplicationEvent;
import com.markingsystem.repository.ExtraScoreApplicationEventRepository;
import com.markingsystem.util.RequestUserContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExtraScoreApplicationEventServiceTest {

    @Test
    void incrementsRevisionWhenApplicationIsSubmittedAgain() {
        List<ExtraScoreApplicationEvent> saved = new ArrayList<>();
        ExtraScoreApplicationEventRepository repository = repository(saved);
        ExtraScoreApplicationEventService service = new ExtraScoreApplicationEventService(
                repository,
                new ObjectMapper()
        );
        ExtraScoreApplication application = new ExtraScoreApplication();
        application.setId(9L);
        application.setStatus("pending");
        application.setReviewStage("committee_pending");
        application.setCurrentRevisionNo(1);

        service.recordSubmission(
                application,
                "draft",
                new RequestUserContext(1L, "STUDENT", null, "数据241", "20240001"),
                "测试学生"
        );
        service.recordSubmission(
                application,
                "rejected",
                new RequestUserContext(1L, "STUDENT", null, "数据241", "20240001"),
                "测试学生"
        );

        assertEquals(2, saved.size());
        assertEquals("SUBMITTED", saved.get(0).getEventType());
        assertEquals("RESUBMITTED", saved.get(1).getEventType());
        assertEquals(2, saved.get(1).getRevisionNo());
        assertEquals(2, application.getCurrentRevisionNo());
    }

    @SuppressWarnings("unchecked")
    private ExtraScoreApplicationEventRepository repository(List<ExtraScoreApplicationEvent> saved) {
        return (ExtraScoreApplicationEventRepository) Proxy.newProxyInstance(
                ExtraScoreApplicationEventRepository.class.getClassLoader(),
                new Class<?>[]{ExtraScoreApplicationEventRepository.class},
                (proxy, method, args) -> {
                    if ("save".equals(method.getName())) {
                        ExtraScoreApplicationEvent event = (ExtraScoreApplicationEvent) args[0];
                        saved.add(event);
                        return event;
                    }
                    if ("existsByApplicationIdAndEventTypeIn".equals(method.getName())) {
                        Collection<String> eventTypes = (Collection<String>) args[1];
                        return saved.stream().anyMatch(event -> eventTypes.contains(event.getEventType()));
                    }
                    if ("toString".equals(method.getName())) {
                        return "ExtraScoreApplicationEventRepositoryTestProxy";
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
