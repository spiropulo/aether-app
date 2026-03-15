package com.aether.app.auth;

import com.aether.app.common.PageInput;
import com.aether.app.common.PagedResult;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Mono;

@Controller
public class UserProfileGraphqlController {

    private final UserProfileService userProfileService;

    public UserProfileGraphqlController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @QueryMapping
    public Mono<PagedResult<UserProfile>> userProfiles(@Argument String tenantId,
                                                        @Argument PageInput page,
                                                        @Argument String search) {
        return userProfileService.getUserProfiles(tenantId, page, search);
    }

    @QueryMapping
    public Mono<UserProfile> userProfile(@Argument String id, @Argument String tenantId) {
        return userProfileService.getUserProfile(id, tenantId);
    }

    @MutationMapping
    public Mono<AuthPayload> register(@Argument RegisterInput input) {
        return userProfileService.register(input);
    }

    @MutationMapping
    public Mono<AuthPayload> login(@Argument LoginInput input) {
        return userProfileService.login(input);
    }

    @MutationMapping
    public Mono<UserProfile> updateProfile(@Argument String id,
                                            @Argument String tenantId,
                                            @Argument UpdateProfileInput input) {
        return userProfileService.updateProfile(id, tenantId, input);
    }

    @MutationMapping
    public Mono<Boolean> changePassword(@Argument String id,
                                         @Argument String tenantId,
                                         @Argument ChangePasswordInput input) {
        return userProfileService.changePassword(id, tenantId, input);
    }

    @MutationMapping
    public Mono<UserProfile> addMember(@Argument String organizationName, @Argument AddMemberInput input) {
        return userProfileService.addMember(organizationName, input);
    }

    @MutationMapping
    public Mono<Boolean> logout(@Argument String id, @Argument String tenantId) {
        return userProfileService.logout(id, tenantId);
    }

    @MutationMapping
    public Mono<Boolean> deleteProfile(@Argument String id, @Argument String tenantId) {
        return userProfileService.deleteProfile(id, tenantId);
    }
}
