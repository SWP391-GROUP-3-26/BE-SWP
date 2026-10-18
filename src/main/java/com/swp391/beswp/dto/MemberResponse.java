package com.swp391.beswp.dto;

import com.swp391.beswp.entity.User;
import com.swp391.beswp.entity.MemberSubscription;
import com.swp391.beswp.entity.ClassBooking;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class MemberResponse {
    private boolean success;
    private String message;
    private MemberData data;

    public static MemberResponse success(User user) {
        return new MemberResponse(true, "Lay thong tin hoc vien thanh cong", MemberData.fromEntity(user));
    }

    public static MemberResponse success(User user, List<PackageData> packages, List<ClassData> classes) {
        return new MemberResponse(true, "Lay thong tin hoc vien thanh cong",
                MemberData.fromEntity(user, packages, classes));
    }

    @Getter
    @AllArgsConstructor
    public static class MemberData {
        private Integer userId;
        private String fullName;
        private String username;
        private String email;
        private String phone;
        private LocalDate dob;
        private String gender;
        private String address;
        private String avatarUrl;
        private String role;
        private String status;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private List<PackageData> activePackages;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private List<ClassData> activeClasses;

        public static MemberData fromEntity(User user) {
            return fromEntity(user, null, null);
        }

        public static MemberData fromEntity(User user, List<PackageData> packages, List<ClassData> classes) {
            return new MemberData(user.getId(), user.getFullName(), user.getUsername(), user.getEmail(),
                    user.getPhone(), user.getDob(), user.getGender(), user.getAddress(), user.getAvatarUrl(),
                    user.getRole().getRoleName(), user.getStatus(), packages, classes);
        }
    }

    public record PackageData(Integer subscriptionId, Integer packageId, String packageName,
                              Integer includedClasses, LocalDate startDate, LocalDate endDate) {
        public static PackageData fromEntity(MemberSubscription subscription) {
            var pack = subscription.getMembershipPackage();
            return new PackageData(subscription.getId(), pack.getId(), pack.getName(), pack.getIncludedClasses(),
                    subscription.getStartDate(), subscription.getEndDate());
        }
    }

    public record ClassData(Integer classId, String className, String subjectName, String coachName,
                            String roomName, String daysOfWeek, LocalDate startDate, LocalDate endDate,
                            LocalTime startTime, LocalTime endTime) {
        public static ClassData fromBooking(ClassBooking booking) {
            var session = booking.getSession();
            var course = session.getClassEntity();
            return new ClassData(course.getId(), course.getName(), course.getSubject().getName(),
                    course.getCoach().getFullName(), course.getRoom().getName(), course.getDaysOfWeek(),
                    course.getDate(), course.getEndDate(),
                    course.getStartTime() == null ? session.getStartTime() : course.getStartTime(),
                    course.getEndTime() == null ? session.getEndTime() : course.getEndTime());
        }
    }
}
