package com.aether.app.graphql;

import graphql.GraphqlErrorBuilder;
import graphql.GraphQLError;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.execution.DataFetcherExceptionResolver;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Maps {@link IllegalArgumentException} from data fetchers to a GraphQL error with a clear
 * {@code message} and {@link ErrorType#BAD_REQUEST} so clients and tools see the reason instead
 * of an unresolved internal error.
 */
@Component
public class IllegalArgumentDataFetcherExceptionResolver implements DataFetcherExceptionResolver {

    @Override
    public Mono<List<GraphQLError>> resolveException(Throwable ex, DataFetchingEnvironment env) {
        IllegalArgumentException iae = unwrapIllegalArgument(ex);
        if (iae == null) {
            return Mono.empty();
        }
        GraphQLError error = GraphqlErrorBuilder.newError(env)
                .errorType(ErrorType.BAD_REQUEST)
                .message(iae.getMessage())
                .build();
        return Mono.just(List.of(error));
    }

    private static IllegalArgumentException unwrapIllegalArgument(Throwable ex) {
        Throwable t = ex;
        while (t != null) {
            if (t instanceof IllegalArgumentException iae) {
                return iae;
            }
            t = t.getCause();
        }
        return null;
    }
}
