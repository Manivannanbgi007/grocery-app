package com.grocery.delivery.service;

import com.grocery.delivery.dto.request.KycRequest;
import com.grocery.delivery.dto.response.AvailabilityResponse;
import com.grocery.delivery.dto.response.EarningsResponse;
import com.grocery.delivery.dto.response.PartnerResponse;

import java.time.LocalDate;

/**
 * The delivery partner's own profile, KYC, online status and earnings (partner user id from X-User-Id).
 */
public interface PartnerProfileService {

    PartnerResponse getMyProfile(Long partnerUserId);

    /** Submit (or resubmit after a rejection) the KYC details -> PENDING until an admin decides. */
    PartnerResponse submitKyc(Long partnerUserId, KycRequest request);

    /** Only approved partners. Also offers them the oldest waiting delivery of their zone. */
    AvailabilityResponse goOnline(Long partnerUserId);

    AvailabilityResponse goOffline(Long partnerUserId);

    AvailabilityResponse getAvailability(Long partnerUserId);

    /** Earnings between two dates (both included), one line per day. */
    EarningsResponse getEarnings(Long partnerUserId, LocalDate from, LocalDate to);
}
