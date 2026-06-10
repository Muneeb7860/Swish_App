package com.platform.notification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class NotificationApplicationTest {

    @Test
    public void testApplicationContext() {
        NotificationApplication app = new NotificationApplication();
        assertNotNull(app);
    }
}
