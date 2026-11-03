package com.grocery.auth.social;

/**
 * Who the provider says the person is, the same for every provider (SocialProfiles makes it).
 *
 * @param provider        google, microsoft, facebook, apple (or test locally)
 * @param providerUserId  the provider's stable id of the person ("sub"): the only thing a login is found by
 * @param email           may be null (a Facebook account made with a phone number, a user who declined to share it)
 * @param emailVerified   true only when the provider says it checked that the person owns the address (the rules
 *                        per provider are in SocialProfiles); only then may a login be linked to an existing account
 * @param fullName        may be null
 */
public record SocialProfile(String provider, String providerUserId, String email, boolean emailVerified, String fullName) {

    public SocialProfile {
        email = email == null || email.isBlank() ? null : email.trim().toLowerCase();
        fullName = fullName == null || fullName.isBlank() ? null : fullName.trim();
        emailVerified = emailVerified && email != null;
    }
}
