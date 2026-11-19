package org.example.lab1;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private long createItem(String name, String description) throws Exception {
        Item item = new Item(null, name, description);
        MvcResult result = mockMvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(item)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void testCreateAndGetItem() throws Exception {
        Item item = new Item(null, "Test Item", "Test Description");

        mockMvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(item)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Test Item"));

        mockMvc.perform(get("/api/items"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    void testGetItemById() throws Exception {
        long id = createItem("Book", "Some book");

        mockMvc.perform(get("/api/items/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Book"))
                .andExpect(jsonPath("$.description").value("Some book"));
    }

    @Test
    void testGetItemByIdNotFound() throws Exception {
        mockMvc.perform(get("/api/items/{id}", 999999))
                .andExpect(status().isNotFound());
    }

    @Test
    void testUpdateItem() throws Exception {
        long id = createItem("Old name", "Old description");
        Item updated = new Item(null, "New name", "New description");

        mockMvc.perform(put("/api/items/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("New name"));

        mockMvc.perform(get("/api/items/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("New description"));
    }

    @Test
    void testUpdateItemNotFound() throws Exception {
        Item updated = new Item(null, "Name", "Description");

        mockMvc.perform(put("/api/items/{id}", 999999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isNotFound());
    }

    @Test
    void testDeleteItem() throws Exception {
        long id = createItem("To delete", "Temp");

        mockMvc.perform(delete("/api/items/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/items/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void testDeleteItemNotFound() throws Exception {
        mockMvc.perform(delete("/api/items/{id}", 999999))
                .andExpect(status().isNotFound());
    }
}
