package com.aether.app.auth;

import com.aether.app.common.PageInput;
import com.aether.app.common.PagedResult;
import com.aether.app.tenant.CreateTenantInput;
import com.aether.app.tenant.TenantRepository;
import com.aether.app.tenant.TenantService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;
    private final TenantRepository tenantRepository;
    private final TenantService tenantService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserProfileService(UserProfileRepository userProfileRepository,
                               TenantRepository tenantRepository,
                               TenantService tenantService,
                               PasswordEncoder passwordEncoder,
                               JwtService jwtService) {
        this.userProfileRepository = userProfileRepository;
        this.tenantRepository = tenantRepository;
        this.tenantService = tenantService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    private Mono<String> resolveTenantIdFromOrganizationName(String organizationName) {
        return tenantRepository.findByOrganizationName(organizationName)
                .map(t -> t.getTenantId())
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Organization '" + organizationName + "' not found. Register first to create it.")));
    }

    /**
     * Resolves tenantId by organization name. If the organization does not exist,
     * creates it automatically. Used during registration only.
     */
    private Mono<String> resolveOrCreateTenantIdForRegistration(String organizationName, String registrantEmail) {
        return tenantRepository.findByOrganizationName(organizationName)
                .map(t -> t.getTenantId())
                .switchIfEmpty(Mono.defer(() -> {
                    CreateTenantInput input = new CreateTenantInput();
                    input.setTenantId(slugify(organizationName));
                    input.setOrganizationName(organizationName);
                    input.setEmail(registrantEmail);
                    input.setDisplayName(organizationName);
                    return tenantService.createTenant(input)
                            .map(t -> t.getTenantId());
                }));
    }

    private static String slugify(String name) {
        if (name == null || name.isBlank()) {
            return "org";
        }
        String slug = name.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        return slug.isBlank() ? "org" : slug;
    }

    public Mono<PagedResult<UserProfile>> getUserProfiles(String tenantId, PageInput page, String search) {
        int limit = PagedResult.effectiveLimit(page);
        int offset = PagedResult.effectiveOffset(page);

        return userProfileRepository.findAllByTenantId(tenantId)
                .filter(p -> matchesSearch(p, search))
                .collectList()
                .map(all -> {
                    int total = all.size();
                    List<UserProfile> slice = all.stream()
                            .skip(offset)
                            .limit(limit)
                            .collect(Collectors.toList());
                    return new PagedResult<>(slice, total, limit, offset);
                });
    }

    public Mono<UserProfile> getUserProfile(String id, String tenantId) {
        return userProfileRepository.findByIdAndTenantId(id, tenantId);
    }

    public Mono<AuthPayload> register(RegisterInput input) {
        return resolveOrCreateTenantIdForRegistration(input.getOrganizationName(), input.getEmail())
                .flatMap(tenantId ->
                        userProfileRepository.findByUsernameAndTenantId(input.getUsername(), tenantId)
                                .flatMap(existing -> Mono.<AuthPayload>error(
                                        new IllegalArgumentException("Username '" + input.getUsername() + "' is already taken.")))
                                .switchIfEmpty(
                                        userProfileRepository.findByEmailAndTenantId(input.getEmail(), tenantId)
                                                .flatMap(existing -> Mono.<AuthPayload>error(
                                                        new IllegalArgumentException("Email '" + input.getEmail() + "' is already registered.")))
                                                .switchIfEmpty(Mono.defer(() -> {
                                                    UserProfile profile = new UserProfile();
                                                    profile.setTenantId(tenantId);
                                                    profile.setOrganizationName(input.getOrganizationName());
                                                    profile.setUsername(input.getUsername());
                                                    profile.setPasswordHash(passwordEncoder.encode(input.getPassword()));
                                                    profile.setEmail(input.getEmail());
                                                    profile.setFirstName(input.getFirstName());
                                                    profile.setLastName(input.getLastName());
                                                    profile.setDisplayName(input.getDisplayName());
                                                    profile.setPhoneNumber(input.getPhoneNumber());
                                                    profile.setRole(input.getRole() != null ? input.getRole() : UserRole.MEMBER);
                                                    profile.setStatus(UserStatus.ACTIVE);
                                                    Instant now = Instant.now();
                                                    profile.setCreatedAt(now);
                                                    profile.setUpdatedAt(now);

                                                    profile.setLoggedIn(true);
                                                    return userProfileRepository.save(profile)
                                                            .map(saved -> new AuthPayload(jwtService.generateToken(saved), saved));
                                                }))
                                ));
    }

    public Mono<UserProfile> addMember(String organizationName, AddMemberInput input) {
        return resolveTenantIdFromOrganizationName(organizationName)
                .flatMap(tenantId ->
                        userProfileRepository.findByUsernameAndTenantId(input.getUsername(), tenantId)
                                .flatMap(existing -> Mono.<UserProfile>error(
                                        new IllegalArgumentException("Username '" + input.getUsername() + "' is already taken.")))
                                .switchIfEmpty(
                                        userProfileRepository.findByEmailAndTenantId(input.getEmail(), tenantId)
                                                .flatMap(existing -> Mono.<UserProfile>error(
                                                        new IllegalArgumentException("Email '" + input.getEmail() + "' is already registered.")))
                                                .switchIfEmpty(Mono.defer(() -> {
                                                    UserProfile profile = new UserProfile();
                                                    profile.setTenantId(tenantId);
                                                    profile.setOrganizationName(organizationName);
                                                    profile.setUsername(input.getUsername());
                                                    profile.setPasswordHash(passwordEncoder.encode(input.getPassword()));
                                                    profile.setEmail(input.getEmail());
                                                    profile.setFirstName(input.getFirstName());
                                                    profile.setLastName(input.getLastName());
                                                    profile.setDisplayName(input.getDisplayName());
                                                    profile.setPhoneNumber(input.getPhoneNumber());
                                                    profile.setRole(input.getRole() != null ? input.getRole() : UserRole.MEMBER);
                                                    profile.setStatus(UserStatus.ACTIVE);
                                                    profile.setLoggedIn(false);
                                                    Instant now = Instant.now();
                                                    profile.setCreatedAt(now);
                                                    profile.setUpdatedAt(now);
                                                    return userProfileRepository.save(profile);
                                                }))
                                ));
    }

    public Mono<AuthPayload> login(LoginInput input) {
        return resolveTenantIdFromOrganizationName(input.getOrganizationName())
                .flatMap(tenantId ->
                        userProfileRepository.findByUsernameAndTenantId(input.getUsername(), tenantId)
                                .switchIfEmpty(Mono.error(new IllegalArgumentException("Invalid username or password.")))
                                .flatMap(profile -> {
                                    if (!passwordEncoder.matches(input.getPassword(), profile.getPasswordHash())) {
                                        return Mono.error(new IllegalArgumentException("Invalid username or password."));
                                    }
                                    if (profile.getStatus() != UserStatus.ACTIVE) {
                                        return Mono.error(new IllegalStateException("Account is " + profile.getStatus().name().toLowerCase() + "."));
                                    }
                                    profile.setLastLoginAt(Instant.now());
                                    profile.setLoggedIn(true);
                                    return userProfileRepository.save(profile)
                                            .map(saved -> new AuthPayload(jwtService.generateToken(saved), saved));
                                }));
    }

    public Mono<UserProfile> updateProfile(String id, String tenantId, UpdateProfileInput input) {
        return userProfileRepository.findByIdAndTenantId(id, tenantId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("User profile not found.")))
                .flatMap(existing -> {
                    if (input.getEmail() != null) {
                        existing.setEmail(input.getEmail());
                    }
                    if (input.getFirstName() != null) {
                        existing.setFirstName(input.getFirstName());
                    }
                    if (input.getLastName() != null) {
                        existing.setLastName(input.getLastName());
                    }
                    if (input.getDisplayName() != null) {
                        existing.setDisplayName(input.getDisplayName());
                    }
                    if (input.getPhoneNumber() != null) {
                        existing.setPhoneNumber(input.getPhoneNumber());
                    }
                    if (input.getAvatarUrl() != null) {
                        existing.setAvatarUrl(input.getAvatarUrl());
                    }
                    if (input.getRole() != null) {
                        existing.setRole(input.getRole());
                    }
                    if (input.getStatus() != null) {
                        existing.setStatus(input.getStatus());
                    }
                    existing.setUpdatedAt(Instant.now());
                    return userProfileRepository.save(existing);
                });
    }

    public Mono<Boolean> changePassword(String id, String tenantId, ChangePasswordInput input) {
        return userProfileRepository.findByIdAndTenantId(id, tenantId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("User profile not found.")))
                .flatMap(existing -> {
                    if (!passwordEncoder.matches(input.getCurrentPassword(), existing.getPasswordHash())) {
                        return Mono.error(new IllegalArgumentException("Current password is incorrect."));
                    }
                    existing.setPasswordHash(passwordEncoder.encode(input.getNewPassword()));
                    existing.setUpdatedAt(Instant.now());
                    return userProfileRepository.save(existing).thenReturn(true);
                });
    }

    public Mono<Boolean> logout(String id, String tenantId) {
        return userProfileRepository.findByIdAndTenantId(id, tenantId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("User profile not found.")))
                .flatMap(existing -> {
                    existing.setLoggedIn(false);
                    existing.setLastLogoutAt(Instant.now());
                    existing.setUpdatedAt(Instant.now());
                    return userProfileRepository.save(existing).thenReturn(true);
                });
    }

    public Mono<Boolean> deleteProfile(String id, String tenantId) {
        return userProfileRepository.findByIdAndTenantId(id, tenantId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("User profile not found.")))
                .flatMap(existing -> userProfileRepository.delete(existing).thenReturn(true));
    }

    private boolean matchesSearch(UserProfile profile, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String term = search.toLowerCase();
        return contains(profile.getUsername(), term)
                || contains(profile.getEmail(), term)
                || contains(profile.getFirstName(), term)
                || contains(profile.getLastName(), term)
                || contains(profile.getDisplayName(), term);
    }

    private boolean contains(String field, String term) {
        return field != null && field.toLowerCase().contains(term);
    }
}
