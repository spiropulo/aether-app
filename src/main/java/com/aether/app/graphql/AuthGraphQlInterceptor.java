package com.aether.app.graphql;

import com.aether.app.auth.JwtService;
import io.jsonwebtoken.Claims;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Puts {@code authUserId}, {@code authTenantId}, and {@code authRole} from a valid Bearer JWT
 * into the GraphQL context so resolvers can scope data to the signed-in user.
 */
@Component
public class AuthGraphQlInterceptor implements WebGraphQlInterceptor {

    private final JwtService jwtService;

    public AuthGraphQlInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = header.substring(7).trim();
            if (!token.isEmpty()) {
                try {
                    Claims claims = jwtService.validateAndExtractClaims(token);
                    String userId = claims.getSubject();
                    String tenantId = claims.get("tenantId", String.class);
                    String role = claims.get("role", String.class);
                    Map<String, Object> ctx = new HashMap<>();
                    if (userId != null) {
                        ctx.put("authUserId", userId);
                    }
                    if (tenantId != null) {
                        ctx.put("authTenantId", tenantId);
                    }
                    if (role != null) {
                        ctx.put("authRole", role);
                    }
                    if (!ctx.isEmpty()) {
                        request.configureExecutionInput((executionInput, builder) -> builder.graphQLContext(ctx).build());
                    }
                } catch (Exception ignored) {
                    // Invalid or expired token — leave context unset; resolvers treat as unauthenticated.
                }
            }
        }
        return chain.next(request);
    }
}
