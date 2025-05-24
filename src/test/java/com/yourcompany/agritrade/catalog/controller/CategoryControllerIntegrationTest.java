package com.yourcompany.agritrade.catalog.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.agritrade.AgriTradeApplication;
import com.yourcompany.agritrade.catalog.dto.request.CategoryRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest(classes = AgriTradeApplication.class)
@AutoConfigureMockMvc
@Testcontainers
public class CategoryControllerIntegrationTest {

    @Container
    public static MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mysqlContainer::getUsername);
        registry.add("spring.datasource.password", mysqlContainer::getPassword);
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Test
    void testCreateCategory_success() throws Exception {
        CategoryRequest createRequest = new CategoryRequest();
        createRequest.setName("Test Category");
        createRequest.setDescription("This is a test category");
        createRequest.setParentId(null); // Assuming root category

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.name").value("Test Category"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.description").value("This is a test category"));
    }

    @Test
    void testGetAllCategories_success() throws Exception {
        // Create a few categories
        CategoryRequest createRequest1 = new CategoryRequest();
        createRequest1.setName("Category 1");
        createRequest1.setDescription("Description 1");
        createRequest1.setParentId(null);

        CategoryRequest createRequest2 = new CategoryRequest();
        createRequest2.setName("Category 2");
        createRequest2.setDescription("Description 2");
        createRequest2.setParentId(null);

        mockMvc.perform(post("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest1)));
        mockMvc.perform(post("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest2)));

        mockMvc.perform(get("/api/categories"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(2)); // Assuming no other categories exist initially
    }
}