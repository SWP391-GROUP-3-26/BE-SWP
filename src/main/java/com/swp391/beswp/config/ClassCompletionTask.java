package com.swp391.beswp.config;

import com.swp391.beswp.service.ClassCompletionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@RequiredArgsConstructor
public class ClassCompletionTask {
    private final ClassCompletionService completion;

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Ho_Chi_Minh")
    public void completeExpiredClasses() {
        completion.completeExpiredClasses();
    }
}
