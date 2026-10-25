package com.rgonz.pricing.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rgonz.pricing.TestcontainersConfig;
import com.rgonz.pricing.service.PriceCacheService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestcontainersConfig.class, ProductRuleControllerTest.FixedClockConfig.class})
class ProductRuleControllerTest {

    private static final String RULES = "/api/v1/product-rules";
    private static final Instant NOW = Instant.parse("2026-10-25T12:00:00.123456789Z");
    private static final String NOW_AS_STORED = "2026-10-25T12:00:00.123456Z";

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoSpyBean
    private PriceCacheService priceCache;

    @Test
    void listsSeededRulesOrderedBySku() throws Exception {
        mockMvc.perform(get(RULES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].sku", contains("ABC-123", "LMN-456", "OLD-001", "XYZ-789")));
    }

    @Test
    void createsRuleAndReturnsLocation() throws Exception {
        mockMvc.perform(json(post(RULES), """
                        {"sku": "NEW-100", "name": "Parlante", "marginPercentage": 12.5, "preferredProvider": "PROVIDER_B"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith(RULES + "/NEW-100")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.sku").value("NEW-100"))
                .andExpect(jsonPath("$.marginPercentage").value(12.5))
                .andExpect(jsonPath("$.preferredProvider").value("PROVIDER_B"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.updatedAt").value(NOW_AS_STORED));
    }

    @Test
    void rejectsDuplicateSkuWithConflict() throws Exception {
        mockMvc.perform(json(post(RULES), """
                        {"sku": "ABC-123", "name": "Otro", "marginPercentage": 5, "preferredProvider": "AUTO"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SKU_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.detail").value("Ya existe una regla para el SKU ABC-123"));
    }

    @Test
    void rejectsInvalidFieldsWithOneErrorPerField() throws Exception {
        mockMvc.perform(json(post(RULES), """
                        {"sku": "abc 1", "name": " Parlante", "marginPercentage": 150.555, "preferredProvider": "AUTO"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.instance").value(RULES))
                .andExpect(jsonPath("$.errors[*].field", hasItem("sku")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("name")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("marginPercentage")));
    }

    @Test
    void reportsEmptyNameWithASingleError() throws Exception {
        mockMvc.perform(json(post(RULES), """
                        {"sku": "NEW-102", "name": "", "marginPercentage": 10, "preferredProvider": "AUTO"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", contains("name")))
                .andExpect(jsonPath("$.errors[0].message").value("debe tener entre 1 y 120 caracteres"));
    }

    @Test
    void rejectsUnknownPreferredProviderAsFieldError() throws Exception {
        mockMvc.perform(json(post(RULES), """
                        {"sku": "NEW-101", "name": "Parlante", "marginPercentage": 10, "preferredProvider": "PROVIDER_C"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("preferredProvider"));
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(json(post(RULES), "{\"sku\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void updatesRuleAndEvictsCachedPrice() throws Exception {
        mockMvc.perform(json(put(RULES + "/ABC-123"), """
                        {"name": "Auriculares BT", "marginPercentage": 20, "preferredProvider": "PROVIDER_A", "active": false}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marginPercentage").value(20))
                .andExpect(jsonPath("$.preferredProvider").value("PROVIDER_A"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.updatedAt").value(NOW_AS_STORED));

        verify(priceCache).evict("ABC-123");
    }

    @Test
    void rejectsUpdateWithMissingFields() throws Exception {
        mockMvc.perform(json(put(RULES + "/ABC-123"), """
                        {"name": "Auriculares BT", "marginPercentage": 20, "preferredProvider": "AUTO"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("active")));

        verify(priceCache, never()).evict(anyString());
    }

    @Test
    void returnsNotFoundWhenUpdatingUnknownSku() throws Exception {
        mockMvc.perform(json(put(RULES + "/NOPE-1"), """
                        {"name": "X", "marginPercentage": 1, "preferredProvider": "AUTO", "active": true}
                        """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_RULE_NOT_FOUND"));

        verify(priceCache, never()).evict(anyString());
    }

    @Test
    void deletesRuleAndEvictsCachedPrice() throws Exception {
        mockMvc.perform(delete(RULES + "/XYZ-789")).andExpect(status().isNoContent());

        mockMvc.perform(get(RULES)).andExpect(jsonPath("$[*].sku", not(hasItem("XYZ-789"))));
        verify(priceCache).evict("XYZ-789");
    }

    @Test
    void returnsNotFoundWhenDeletingUnknownSku() throws Exception {
        mockMvc.perform(delete(RULES + "/NOPE-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_RULE_NOT_FOUND"));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
