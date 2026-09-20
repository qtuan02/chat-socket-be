package com.chat_socket.security;

import com.chat_socket.constant.RouteApi;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * User-uploaded files are served from the API origin, so a browser that navigates to one directly
 * must never render or execute it (an uploaded .html/.svg would run same-origin with the API).
 * {@code <img>}/{@code <video>} embedding is unaffected: browsers ignore Content-Disposition for
 * embedded resource fetches, only for top-level navigation and downloads.
 */
@Component
public class FileDownloadHeadersFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/api" + RouteApi.FILES + "/")) {
            response.setHeader("Content-Disposition", "attachment");
            response.setHeader("X-Content-Type-Options", "nosniff");
        }
        filterChain.doFilter(request, response);
    }
}
