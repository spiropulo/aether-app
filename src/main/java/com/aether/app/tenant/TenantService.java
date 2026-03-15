package com.aether.app.tenant;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final PubSubTemplate pubSubTemplate;
    private final String tenantEventsTopic;

    public TenantService(TenantRepository tenantRepository,
                         PubSubTemplate pubSubTemplate,
                         @Value("${aether.pubsub.tenant-topic:}") String tenantEventsTopic) {
        this.tenantRepository = tenantRepository;
        this.pubSubTemplate = pubSubTemplate;
        this.tenantEventsTopic = tenantEventsTopic;
    }

    public Flux<Tenant> getTenantsForTenant(String tenantId) {
        return tenantRepository.findAllByTenantId(tenantId);
    }

    public Mono<Tenant> getTenant(String id, String tenantId) {
        return tenantRepository.findByIdAndTenantId(id, tenantId);
    }

    public Mono<Tenant> createTenant(CreateTenantInput input) {
        Tenant tenant = new Tenant();
        tenant.setTenantId(input.getTenantId());
        tenant.setOrganizationName(input.getOrganizationName());
        tenant.setEmail(input.getEmail());
        tenant.setDisplayName(input.getDisplayName());
        tenant.setSubscriptionPlan(input.getSubscriptionPlan());
        tenant.setStatus(TenantStatus.ACTIVE);
        Instant now = Instant.now();
        tenant.setCreatedAt(now);
        tenant.setUpdatedAt(now);

        return tenantRepository.save(tenant)
                .doOnSuccess(saved -> publishEvent("TENANT_CREATED", saved));
    }

    public Mono<Tenant> updateTenant(String id, String tenantId, UpdateTenantInput input) {
        return tenantRepository.findByIdAndTenantId(id, tenantId)
                .flatMap(existing -> {
                    if (input.getOrganizationName() != null) {
                        existing.setOrganizationName(input.getOrganizationName());
                    }
                    if (input.getDisplayName() != null) {
                        existing.setDisplayName(input.getDisplayName());
                    }
                    if (input.getSubscriptionPlan() != null) {
                        existing.setSubscriptionPlan(input.getSubscriptionPlan());
                    }
                    if (input.getStatus() != null) {
                        existing.setStatus(input.getStatus());
                    }
                    existing.setUpdatedAt(Instant.now());
                    return tenantRepository.save(existing)
                            .doOnSuccess(saved -> publishEvent("TENANT_UPDATED", saved));
                });
    }

    public Mono<Boolean> deleteTenant(String id, String tenantId) {
        return tenantRepository.findByIdAndTenantId(id, tenantId)
                .flatMap(existing ->
                        tenantRepository.delete(existing)
                                .thenReturn(existing)
                )
                .doOnSuccess(tenant -> publishEvent("TENANT_DELETED", tenant))
                .map(tenant -> true);
    }

    private void publishEvent(String eventType, Tenant tenant) {
        if (tenantEventsTopic == null || tenantEventsTopic.isBlank()) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", eventType);
        payload.put("id", tenant.getId());
        payload.put("tenantId", tenant.getTenantId());
        payload.put("email", tenant.getEmail());
        payload.put("subscriptionPlan", tenant.getSubscriptionPlan());

        pubSubTemplate.publish(tenantEventsTopic, payload.toString());
    }
}
