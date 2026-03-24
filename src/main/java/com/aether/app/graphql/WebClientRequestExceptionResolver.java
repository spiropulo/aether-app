package com.aether.app.graphql;

import graphql.GraphqlErrorBuilder;
import graphql.GraphQLError;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.execution.DataFetcherExceptionResolver;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Maps {@link WebClientRequestException} (e.g. SMS mock unreachable) to a GraphQL {@code BAD_REQUEST}
 * so clients see a clear message instead of an unresolved internal error.
 */
@Component
public class WebClientRequestExceptionResolver implements DataFetcherExceptionResolver {

    @Override
    public Mono<List<GraphQLError>> resolveException(Throwable ex, DataFetchingEnvironment env) {
        WebClientRequestException wcre = unwrap(ex, WebClientRequestException.class);
        if (wcre == null) {
            return Mono.empty();
        }
        String detail = wcre.getMessage() != null ? wcre.getMessage() : wcre.getClass().getSimpleName();
        String message;
        if (detail.contains("Connection refused") && detail.contains("4010")) {
            message = "Cannot reach the SMS mock on port 4010 (connection refused). From the aether-app directory run: "
                    + "docker compose up -d twilio-mock — then confirm the container is running (docker compose ps).";
        } else if (detail.contains("Connection refused")) {
            message = "Cannot reach the configured SMS API: " + detail
                    + " If using local Prism, start it with docker compose up -d twilio-mock.";
        } else {
            message = "Outbound HTTP request failed: " + detail;
        }
        GraphQLError error = GraphqlErrorBuilder.newError(env)
                .errorType(ErrorType.BAD_REQUEST)
                .message(message)
                .build();
        return Mono.just(List.of(error));
    }

    private static <T extends Throwable> T unwrap(Throwable ex, Class<T> type) {
        Throwable t = ex;
        while (t != null) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
            t = t.getCause();
        }
        return null;
    }
}
