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
        return tenantRepository.findAllByTenantId(input.getTenantId())
                .hasElements()
                .flatMap(alreadyExists -> {
                    if (Boolean.TRUE.equals(alreadyExists)) {
                        return Mono.error(new IllegalStateException(
                                "A workspace tenant already exists for this organization. Only updates are allowed."));
                    }
                    return persistNewTenant(input);
                });
    }

    private Mono<Tenant> persistNewTenant(CreateTenantInput input) {
        Tenant tenant = new Tenant();
        tenant.setTenantId(input.getTenantId());
        tenant.setOrganizationName(input.getOrganizationName());
        tenant.setEmail(input.getEmail());
        tenant.setDisplayName(input.getDisplayName());
        tenant.setSubscriptionPlan(input.getSubscriptionPlan());
        tenant.setPhoneNumber(input.getPhoneNumber());
        tenant.setAddressLine1(input.getAddressLine1());
        tenant.setAddressLine2(input.getAddressLine2());
        tenant.setCity(input.getCity());
        tenant.setState(input.getState());
        tenant.setPostalCode(input.getPostalCode());
        tenant.setCountry(input.getCountry());
        tenant.setStatus(TenantStatus.ACTIVE);
        Instant now = Instant.now();
        tenant.setCreatedAt(now);
        tenant.setUpdatedAt(now);

        return tenantRepository.save(tenant)
                .doOnSuccess(saved -> publishEvent("TENANT_CREATED", saved));
    }

    public Mono<Tenant> updateTenant(String id, String tenantId, UpdateTenantInput input) {
        return tenantRepository.findByIdAndTenantId(id, tenantId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Workspace tenant not found for this id and organization. Refresh the page and try again.")))
                .flatMap(existing -> {
                    if (input.getOrganizationName() != null) {
                        existing.setOrganizationName(input.getOrganizationName());
                    }
                    if (input.getDisplayName() != null) {
                        existing.setDisplayName(input.getDisplayName());
                    }
                    if (input.getEmail() != null) {
                        String email = blankToNull(input.getEmail());
                        if (email == null) {
                            throw new IllegalArgumentException("Tenant account email cannot be empty.");
                        }
                        existing.setEmail(email);
                    }
                    if (input.getSubscriptionPlan() != null) {
                        existing.setSubscriptionPlan(input.getSubscriptionPlan());
                    }
                    if (input.getStatus() != null) {
                        existing.setStatus(input.getStatus());
                    }
                    if (input.getPhoneNumber() != null) {
                        existing.setPhoneNumber(blankToNull(input.getPhoneNumber()));
                    }
                    if (input.getAddressLine1() != null) {
                        existing.setAddressLine1(blankToNull(input.getAddressLine1()));
                    }
                    if (input.getAddressLine2() != null) {
                        existing.setAddressLine2(blankToNull(input.getAddressLine2()));
                    }
                    if (input.getCity() != null) {
                        existing.setCity(blankToNull(input.getCity()));
                    }
                    if (input.getState() != null) {
                        existing.setState(blankToNull(input.getState()));
                    }
                    if (input.getPostalCode() != null) {
                        existing.setPostalCode(blankToNull(input.getPostalCode()));
                    }
                    if (input.getCountry() != null) {
                        existing.setCountry(blankToNull(input.getCountry()));
                    }
                    existing.setUpdatedAt(Instant.now());
                    requireWorkspaceContact(existing);
                    return tenantRepository.save(existing)
                            .doOnSuccess(saved -> publishEvent("TENANT_UPDATED", saved));
                });
    }

    /**
     * Every workspace must have a company phone and a minimal business address after any update.
     */
    private static void requireWorkspaceContact(Tenant t) {
        if (isBlank(t.getPhoneNumber())
                || isBlank(t.getAddressLine1())
                || isBlank(t.getCity())
                || isBlank(t.getCountry())) {
            throw new IllegalArgumentException(
                    "Workspace must include company phone, street address (line 1), city, and country.");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public Mono<Boolean> deleteTenant(String id, String tenantId) {
        return Mono.error(new IllegalStateException(
                "Deleting the workspace tenant is not supported. Each organization has exactly one tenant."));
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
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
