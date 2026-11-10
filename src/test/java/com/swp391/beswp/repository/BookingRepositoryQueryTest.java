package com.swp391.beswp.repository;

import jakarta.persistence.Entity;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.repository.Query;
import static org.junit.jupiter.api.Assertions.*;

class BookingRepositoryQueryTest {
    @Test
    void hibernateParsesBookingAndPaymentQueriesAgainstMappedSchemaWithoutDatabase() throws Exception {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.SQLServerDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .applySetting("hibernate.connection.provider_class", "org.hibernate.engine.jdbc.connections.internal.UserSuppliedConnectionProviderImpl")
                .build();
        try {
            MetadataSources sources = new MetadataSources(registry);
            var scanner = new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
            for (var candidate : scanner.findCandidateComponents("com.swp391.beswp.entity")) {
                sources.addAnnotatedClass(Class.forName(candidate.getBeanClassName()));
            }
            var metadata = sources.buildMetadata();
            var otpMapping = metadata.getEntityBinding("com.swp391.beswp.entity.EmailOtpChallenge");
            var expiryColumn = otpMapping.getProperty("otpExpiresAt").getColumns().getFirst();
            assertEquals("datetimeoffset(7)", expiryColumn.getSqlType(metadata));
            try (var factory = metadata.buildSessionFactory(); var session = factory.openSession()) {
                for (Class<?> repository : new Class<?>[]{ClassBookingRepository.class, PaymentRepository.class, ClassRepository.class, UserRepository.class,
                        RoomRepository.class, SubjectRepository.class, EmailOtpChallengeRepository.class, UserManagementRepository.class,
                        ClassSessionRepository.class, MemberSubscriptionRepository.class,
                        MemberPackageSubscriptionRepository.class, MembershipIncludedSportRepository.class, MembershipSportSelectionRepository.class}) {
                    for (var method : repository.getDeclaredMethods()) {
                        Query query = method.getAnnotation(Query.class);
                        if (query != null) assertDoesNotThrow(() -> {
                            if (method.isAnnotationPresent(org.springframework.data.jpa.repository.Modifying.class))
                                session.createMutationQuery(query.value());
                            else session.createQuery(query.value(), Object.class);
                        }, method.toString());
                    }
                }
            }
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }
}
