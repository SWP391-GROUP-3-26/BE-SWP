package com.swp391.beswp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bảng trung gian: Package → Sport được bao gồm sẵn (Included Sports).
 * Sport trong bảng này KHÔNG cần Member lựa chọn khi mua Membership.
 * Record này KHÔNG tạo MembershipSportSelection.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "Package_Included_Sport", schema = "dbo")
public class PackageIncludedSport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Package_ID", nullable = false)
    private MembershipPackage membershipPackage;

    /**
     * Sport (Subject) được bao gồm sẵn trong Package.
     * Nếu Sport bị DISCONTINUED, Member của Membership đã mua vẫn được dùng
     * nhưng Sport này không được thêm vào Package mới.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Subject_ID", nullable = false)
    private Subject subject;
}
