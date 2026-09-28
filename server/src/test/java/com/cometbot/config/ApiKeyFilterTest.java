package com.cometbot.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyFilterTest {

    @Test
    void rejectsApiRequestsWithoutTheKey() throws Exception {
        MockHttpServletResponse response = run(new ApiKeyFilter("secret"), "/api/guilds/1/deadlines", null);
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void rejectsTheWrongKey() throws Exception {
        MockHttpServletResponse response = run(new ApiKeyFilter("secret"), "/api/guilds/1/deadlines", "nope");
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void allowsTheRightKey() throws Exception {
        MockHttpServletResponse response = run(new ApiKeyFilter("secret"), "/api/guilds/1/deadlines", "secret");
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void leavesHealthChecksOpen() throws Exception {
        MockHttpServletResponse response = run(new ApiKeyFilter("secret"), "/actuator/health", null);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void isOpenWhenNoKeyIsConfigured() throws Exception {
        MockHttpServletResponse response = run(new ApiKeyFilter(""), "/api/guilds/1/deadlines", null);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private static MockHttpServletResponse run(ApiKeyFilter filter, String uri, String key) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        if (key != null) {
            request.addHeader(ApiKeyFilter.HEADER, key);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
