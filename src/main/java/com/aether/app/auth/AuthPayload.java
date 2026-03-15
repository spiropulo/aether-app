package com.aether.app.auth;

public class AuthPayload {

    private final String token;
    private final UserProfile user;

    public AuthPayload(String token, UserProfile user) {
        this.token = token;
        this.user = user;
    }

    public String getToken() {
        return token;
    }

    public UserProfile getUser() {
        return user;
    }
}
