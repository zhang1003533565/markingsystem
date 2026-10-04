package com.markingsystem.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrustedIdentityHeaderFilterTest {

    @Test
    void trustedIdentityHeadersOverrideClientSuppliedHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "999");
        request.addHeader("X-User-Role", "ADMIN");
        request.addHeader("X-Student-No", "other-student");
        request.addHeader("X-Trace-Id", "trace-1");

        TrustedIdentityHeaderFilter.TrustedIdentityHeaderRequest wrapped =
                new TrustedIdentityHeaderFilter.TrustedIdentityHeaderRequest(request, trustedHeaders());

        assertEquals("10", wrapped.getHeader("X-User-Id"));
        assertEquals("STUDENT", wrapped.getHeader("X-User-Role"));
        assertEquals("20230001", wrapped.getHeader("X-Student-No"));
        assertEquals("trace-1", wrapped.getHeader("X-Trace-Id"));
        assertEquals(List.of("STUDENT"), Collections.list(wrapped.getHeaders("X-User-Role")));
    }

    @Test
    void trustedIdentityHeadersRemoveSpoofedMissingValues() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Teacher-Id", "777");

        TrustedIdentityHeaderFilter.TrustedIdentityHeaderRequest wrapped =
                new TrustedIdentityHeaderFilter.TrustedIdentityHeaderRequest(request, trustedHeaders());

        assertFalse(Collections.list(wrapped.getHeaders("X-Teacher-Id")).contains("777"));
        assertFalse(Collections.list(wrapped.getHeaderNames()).contains("X-Teacher-Id"));
        assertTrue(Collections.list(wrapped.getHeaderNames()).contains("X-User-Id"));
    }

    private Map<String, String> trustedHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-User-Id", "10");
        headers.put("X-User-Role", "STUDENT");
        headers.put("X-Teacher-Id", null);
        headers.put("X-Class-Name", "计科231");
        headers.put("X-Student-No", "20230001");
        return headers;
    }
}
