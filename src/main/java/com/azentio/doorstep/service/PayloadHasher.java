package com.azentio.doorstep.service;

import com.azentio.doorstep.api.dto.ApplicationRequest;
import com.azentio.doorstep.signals.Hashing;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** Canonical JSON (sorted properties) of the parsed request, hashed: equal payloads give equal hashes. */
@Component
public class PayloadHasher {

    private final ObjectMapper canonical;

    public PayloadHasher(ObjectMapper objectMapper) {
        this.canonical = objectMapper.copy().configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
    }

    public String hash(ApplicationRequest request) {
        try {
            return Hashing.sha256(canonical.writeValueAsString(request));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise application " + request.applicationId(), e);
        }
    }
}
