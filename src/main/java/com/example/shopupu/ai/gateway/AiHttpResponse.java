package com.example.shopupu.ai.gateway;

import com.example.shopupu.ai.guard.AiUnavailableException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.springframework.http.client.ClientHttpResponse;

/** Bound provider response allocation independently of a provider respecting its output limit. */
final class AiHttpResponse {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private AiHttpResponse() {
    }

    static <T> T read(ClientHttpResponse response, int maxBytes, Class<T> type) throws IOException {
        if (!response.getStatusCode().is2xxSuccessful()) throw new AiUnavailableException();
        byte[] bytes = response.getBody().readNBytes(maxBytes + 1);
        if (bytes.length > maxBytes) throw new AiUnavailableException();
        return MAPPER.readValue(bytes, type);
    }

    static List<float[]> validateEmbeddings(List<float[]> vectors, int count, int dimensions) {
        if (vectors == null || vectors.size() != count) throw new AiUnavailableException();
        for (float[] vector : vectors) {
            if (vector == null || vector.length != dimensions) throw new AiUnavailableException();
            for (float value : vector) {
                if (!Float.isFinite(value)) throw new AiUnavailableException();
            }
        }
        return vectors;
    }
}
