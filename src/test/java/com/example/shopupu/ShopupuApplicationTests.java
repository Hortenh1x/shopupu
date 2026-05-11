package com.example.shopupu;

import com.example.shopupu.support.PostgresContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
/**
 * describes the ShopupuApplicationTests test class.
 */
class ShopupuApplicationTests extends PostgresContainerSupport {

	@Test
	// handles contextLoads.
	void contextLoads() {
	}

}
