package com.payshield.frauddetector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SimpleContextTest {

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void objectMapperAvailable() {
        assertThat(objectMapper).isNotNull();
    }
}
