package com.swp391.beswp.service;

public final class MembershipFreezePolicy {
    private MembershipFreezePolicy() { }

    public static final int MINIMUM_DURATION_DAYS = 180;
    public static final String TERMS = "Trong thời gian bảo lưu, toàn bộ quyền lợi của gói, bao gồm tập luyện với HLV, "
            + "được tạm dừng. Mọi thỏa thuận tập luyện riêng bên ngoài giữa Member và HLV không thuộc quyền lợi "
            + "của gói và không được hệ thống ghi nhận hoặc bảo đảm. Quyền bảo lưu đã chọn không được hoàn lại "
            + "khi tham gia lại sớm. Ngày hết hạn chỉ được cộng số ngày bảo lưu thực tế đủ 24 giờ.";

    public static String purchaseTerms(String terms, int freezeMonths) {
        if (freezeMonths <= 0) return terms;
        return terms == null || terms.isBlank() ? TERMS : terms + "\n\n" + TERMS;
    }
}
