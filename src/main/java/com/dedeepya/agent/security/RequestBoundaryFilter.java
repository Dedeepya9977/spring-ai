package com.dedeepya.agent.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(-110)
public class RequestBoundaryFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String id = UUID.randomUUID().toString(); // Never trust unsanitized caller IDs in logs.
    response.setHeader("X-Correlation-ID", id);
    request.setAttribute("correlationId", id);
    try (MDC.MDCCloseable ignored = MDC.putCloseable("correlationId", id)) {
      if (request.getContentLengthLong() > 400_000) {
        response.sendError(413);
        return;
      }
      // Bound chunked requests as well as Content-Length requests.
      chain.doFilter(
          new HttpServletRequestWrapper(request) {
            @Override
            public ServletInputStream getInputStream() throws IOException {
              ServletInputStream source = super.getInputStream();
              return new ServletInputStream() {
                private int count;

                @Override
                public int read() throws IOException {
                  int value = source.read();
                  if (value != -1 && ++count > 400_000)
                    throw new IOException("Request body too large");
                  return value;
                }

                @Override
                public boolean isFinished() {
                  return source.isFinished();
                }

                @Override
                public boolean isReady() {
                  return source.isReady();
                }

                @Override
                public void setReadListener(ReadListener listener) {
                  source.setReadListener(listener);
                }
              };
            }
          },
          response);
    }
  }
}
