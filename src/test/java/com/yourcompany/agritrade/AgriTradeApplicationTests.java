package com.yourcompany.agritrade;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class AgriTradeApplicationTests extends AbstractIntegrationTest {

  @Test
  void contextLoads() {}
}
