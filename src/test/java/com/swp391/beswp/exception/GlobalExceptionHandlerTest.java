package com.swp391.beswp.exception;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {
    @Test
    void unexpectedExceptionLogsCauseAndKeepsResponseSafe() {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        try {
            var cause = new java.sql.SQLException("SQL Server diagnostic detail");
            var error = new IllegalStateException("Internal detail", cause);
            var response = new GlobalExceptionHandler().handleUnexpected(error);
            assertEquals(500, response.getStatusCode().value());
            assertNotNull(response.getBody());
            assertEquals("Loi he thong", response.getBody().getMessage());
            assertFalse(response.getBody().isSuccess());
            var logged = events.list.getFirst().getThrowableProxy();
            assertEquals(error.getClass().getName(), logged.getClassName());
            assertEquals(cause.getClass().getName(), logged.getCause().getClassName());
            assertTrue(logged.getStackTraceElementProxyArray().length > 0);
        } finally {
            logger.detachAppender(events);
            events.stop();
        }
    }
}
