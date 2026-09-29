package com.orderhub.backend.catalog;

import com.orderhub.backend.auth.JwtProperties;
import com.orderhub.backend.auth.SecurityConfig;
import com.orderhub.backend.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties(JwtProperties.class)
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-test-secret-test-secret-32b",
        "app.jwt.expiration-minutes=5"
})
class ProductSecurityTest {

    private static final String BODY = "{\"name\":\"Mouse\",\"price\":10,\"stock\":1}";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ProductService service;

    @Test
    void get_isPublic() throws Exception {
        mockMvc.perform(get("/api/products")).andExpect(status().isOk());
    }

    @Test
    void post_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/products").contentType("application/json").content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void post_asCustomer_returns403() throws Exception {
        mockMvc.perform(post("/api/products").contentType("application/json").content(BODY)
                        .with(jwt().jwt(j -> j.claim("role", "CUSTOMER"))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void post_asAdmin_returns201() throws Exception {
        mockMvc.perform(post("/api/products").contentType("application/json").content(BODY)
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isCreated());
    }

    @Test
    void delete_withoutToken_returns401() throws Exception {
        mockMvc.perform(delete("/api/products/1")).andExpect(status().isUnauthorized());
    }
}