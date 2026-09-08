package com.jobtracker.migrationpoc.web;

import com.jobtracker.migrationpoc.observability.RequestTiming;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTimingFilter extends OncePerRequestFilter {
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/") && !request.getRequestURI().equals("/healthz");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        RequestTiming.begin();
        try { chain.doFilter(request, response); }
        finally {
            if (!response.isCommitted()) response.setHeader("Server-Timing", RequestTiming.serverTiming());
            RequestTiming.clear();
        }
    }
}