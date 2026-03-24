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
     * Resolves tenant by organization name. If the organization does not exist, creates it.
     * Used during registration only.
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
                                        new IllegalArgumentException(
                                                "Cannot register: the username "
                                                        + quoteForMessage(input.getUsername())
                                                        + " is already taken in this organization. "
                                                        + "Sign in with that account, or choose a different username.")))
                                .switchIfEmpty(
                                        userProfileRepository.findByEmailAndTenantId(input.getEmail(), tenantId)
                                                .flatMap(existing -> Mono.<AuthPayload>error(
                                                        new IllegalArgumentException(
                                                                "Cannot register: the email address "
                                                                        + quoteForMessage(input.getEmail())
                                                                        + " is already registered in this organization. "
                                                                        + "Sign in, or use a different email address.")))
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
                                                    // Register page: every new account from this mutation is an org admin.
                                                    profile.setRole(UserRole.ADMIN);
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

    public Mono<UserProfile> addMember(String callerId, String tenantId, String organizationName, AddMemberInput input) {
        return userProfileRepository.findByIdAndTenantId(callerId, tenantId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Caller profile not found.")))
                .flatMap(caller -> {
                    if (caller.getRole() != UserRole.ADMIN) {
                        return Mono.error(new IllegalArgumentException("Only admins can add team members."));
                    }
                    return resolveTenantIdFromOrganizationName(organizationName)
                            .flatMap(orgTenantId -> {
                                if (!tenantId.equals(orgTenantId)) {
                                    return Mono.error(new IllegalArgumentException(
                                            "Organization does not match your workspace."));
                                }
                                Mono<Boolean> usernameTaken = userProfileRepository
                                        .findByUsernameAndTenantId(input.getUsername(), orgTenantId)
                                        .hasElement();
                                Mono<Boolean> emailTaken = userProfileRepository
                                        .findByEmailAndTenantId(input.getEmail(), orgTenantId)
                                        .hasElement();
                                return Mono.zip(usernameTaken, emailTaken)
                                        .flatMap(tup -> {
                                            boolean u = Boolean.TRUE.equals(tup.getT1());
                                            boolean e = Boolean.TRUE.equals(tup.getT2());
                                            if (u || e) {
                                                return Mono.error(new IllegalArgumentException(
                                                        buildAddMemberConflictMessage(
                                                                input.getUsername(), input.getEmail(), u, e)));
                                            }
                                            return userProfileRepository.findAllByTenantId(orgTenantId)
                                                    .count()
                                                    .flatMap(userCount -> {
                                                        UserProfile profile = new UserProfile();
                                                        profile.setTenantId(orgTenantId);
                                                        profile.setOrganizationName(organizationName);
                                                        profile.setUsername(input.getUsername());
                                                        profile.setPasswordHash(passwordEncoder.encode(input.getPassword()));
                                                        profile.setEmail(input.getEmail());
                                                        profile.setFirstName(input.getFirstName());
                                                        profile.setLastName(input.getLastName());
                                                        profile.setDisplayName(input.getDisplayName());
                                                        profile.setPhoneNumber(input.getPhoneNumber());
                                                        if (input.getHourlyLaborRate() != null) {
                                                            profile.setHourlyLaborRate(input.getHourlyLaborRate());
                                                        }
                                                        UserRole role = userCount == 0
                                                                ? UserRole.ADMIN
                                                                : (input.getRole() != null ? input.getRole() : UserRole.MEMBER);
                                                        profile.setRole(role);
                                                        profile.setStatus(UserStatus.ACTIVE);
                                                        profile.setLoggedIn(false);
                                                        Instant now = Instant.now();
                                                        profile.setCreatedAt(now);
                                                        profile.setUpdatedAt(now);
                                                        return userProfileRepository.save(profile);
                                                    });
                                        });
                            });
                });
    }

    private static String quoteForMessage(String value) {
        if (value == null || value.isBlank()) {
            return "(empty)";
        }
        return "\"" + value.trim().replace("\"", "'") + "\"";
    }

    /**
     * Explains every conflict when adding a member (username and/or email already used in the tenant).
     */
    private static String buildAddMemberConflictMessage(
            String username, String email, boolean usernameTaken, boolean emailTaken) {
        String u = quoteForMessage(username);
        String em = quoteForMessage(email);
        if (usernameTaken && emailTaken) {
            return "Cannot add this team member: the username "
                    + u
                    + " is already in use in your organization, and the email address "
                    + em
                    + " is already assigned to another member. Each person needs a distinct username and email in "
                    + "your team—change one or both values, or use the existing member instead of creating a duplicate.";
        }
        if (usernameTaken) {
            return "Cannot add this team member: the username "
                    + u
                    + " is already in use in your organization. Usernames must be unique—choose a different username "
                    + "or use the existing team member with that login.";
        }
        return "Cannot add this team member: the email address "
                + em
                + " is already assigned to another member in your organization. Each email may only be used once—"
                + "enter a different email or update the existing member’s profile.";
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

    public Mono<UserProfile> updateProfile(String id, String tenantId, String callerId, UpdateProfileInput input) {
        return userProfileRepository.findByIdAndTenantId(callerId, tenantId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Caller profile not found.")))
                .flatMap(caller -> userProfileRepository.findByIdAndTenantId(id, tenantId)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("User profile not found.")))
                        .flatMap(existing -> {
                            boolean isEditingSelf = callerId.equals(id);
                            boolean callerIsAdmin = caller.getRole() == UserRole.ADMIN;
                            if (!isEditingSelf && !callerIsAdmin) {
                                return Mono.error(new IllegalArgumentException("Only admins can edit other members' profiles."));
                            }
                            boolean canUpdateRoleStatus = callerIsAdmin;
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
                            if (isEditingSelf || callerIsAdmin) {
                                if (input.getHourlyLaborRate() != null) {
                                    existing.setHourlyLaborRate(input.getHourlyLaborRate());
                                } else if (Boolean.TRUE.equals(input.getClearHourlyLaborRate())) {
                                    existing.setHourlyLaborRate(null);
                                }
                            }
                            if (canUpdateRoleStatus && input.getRole() != null) {
                                existing.setRole(input.getRole());
                            }
                            if (canUpdateRoleStatus && input.getStatus() != null) {
                                existing.setStatus(input.getStatus());
                            }
                            existing.setUpdatedAt(Instant.now());
                            return userProfileRepository.save(existing);
                        }));
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
