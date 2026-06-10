package com.platform.gateway;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

public class PlatformGatewayApplicationTest {

    @Test
    public void testMain() {
        try (MockedStatic<SpringApplication> springBootMock = Mockito.mockStatic(SpringApplication.class)) {
            ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
            springBootMock.when(() -> SpringApplication.run(PlatformGatewayApplication.class, new String[]{}))
                    .thenReturn(context);

            PlatformGatewayApplication.main(new String[]{});

            springBootMock.verify(() -> SpringApplication.run(PlatformGatewayApplication.class, new String[]{}), times(1));
        }
    }
}
