package com.swp391.beswp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public final class ReceptionistRegistrationDtos {
    private ReceptionistRegistrationDtos() {
    }

    public record PackageOption(
            Integer packageId,
            String name,
            String description,
            BigDecimal price,
            Integer durationDays,
            Integer includedClasses,
            String benefits,
            String termConditions
    ) {
    }

    public record PackageCatalogResponse(
            boolean success,
            String message,
            List<PackageOption> data
    ) {
    }

    public record PackageRegistrationResponse(
            boolean success,
            String message,
            Integer memberId,
            String memberName,
            Integer subscriptionId,
            Integer packageId,
            String packageName,
            LocalDate startDate,
            LocalDate endDate,
            Integer paymentId,
            BigDecimal amount,
            String paymentMethod,
            String paymentStatus
    ) {
    }

    public record SessionOption(
            Integer sessionId,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            int capacity,
            int availableSeats
    ) {
    }

    public record ClassOption(
            Integer classId,
            String name,
            String subjectName,
            String coachName,
            String roomName,
            String status,
            BigDecimal pricePerSession,
            Integer maxCapacity,
            LocalDate startDate,
            Integer durationWeeks,
            LocalDate endDate,
            String daysOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            int upcomingSessions,
            int availableSeats,
            boolean available
    ) {
    }

    public record ClassCatalogResponse(
            boolean success,
            String message,
            List<ClassOption> data
    ) {
    }

    public record ClassRegistrationPreview(
            boolean success,
            String message,
            Integer memberId,
            String memberName,
            ClassOption classInfo,
            List<SessionOption> sessions,
            boolean alreadyRegistered,
            boolean canRegister,
            boolean canPayWithPackage,
            Integer subscriptionId,
            String packageName,
            Integer totalPackageClasses,
            Integer usedPackageClasses,
            Integer remainingPackageClasses,
            Integer requiredPackageClasses,
            BigDecimal directPaymentAmount,
            List<String> paymentChannels
    ) {
    }

    public record ClassRegistrationResponse(
            boolean success,
            String message,
            Integer registrationId,
            Integer bookingId,
            Integer memberId,
            String memberName,
            Integer classId,
            String className,
            String paymentMethod,
            String paymentChannel,
            Integer sessionCount,
            List<SessionOption> sessions,
            Integer subscriptionId,
            String packageName,
            Integer remainingPackageClasses,
            Integer paymentId,
            BigDecimal paymentAmount,
            String paymentStatus,
            LocalDateTime registeredAt
    ) {
    }
}
