package com.chat_socket.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class FileDownloadHeadersFilterTest {
    private final FileDownloadHeadersFilter filter = new FileDownloadHeadersFilter();

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }

    @Test
    void filesPath_setsAttachmentAndNosniffHeaders() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("/api/files/abc.html"), response, new MockFilterChain());

        assertThat(response.getHeader("Content-Disposition")).isEqualTo("attachment");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    void otherPath_doesNotSetHeaders() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("/api/v1/user/me"), response, new MockFilterChain());

        assertThat(response.getHeader("Content-Disposition")).isNull();
    }

    @Test
    void filesPath_continuesChain() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/api/files/abc.png"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }
}
