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
 * Choice Group của một Package.
 * Mỗi Choice Group yêu cầu Member chọn chính xác {@code requiredSelection} Sport
 * từ danh sách Sport thuộc Group này.
 *
 * <pre>
 * Package D
 *   CHOICE GROUP 1: Land Sports  (requiredSelection = 2)
 *     - Football, Basketball, Tennis
 *   CHOICE GROUP 2: Water Sports  (requiredSelection = 1)
 *     - Swimming, Water Polo
 * </pre>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "Package_Choice_Group", schema = "dbo")
public class PackageChoiceGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Choice_Group_ID")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Package_ID", nullable = false)
    private MembershipPackage membershipPackage;

    @Column(name = "Group_Name", nullable = false, length = 100)
    private String groupName;

    @Column(name = "Description", columnDefinition = "NVARCHAR(MAX)")
    private String description;

    /**
     * Số Sport Member phải chọn chính xác (Exactly N).
     * Không phải tối đa N hay ít nhất N.
     */
    @Column(name = "Required_Selection", nullable = false)
    private Integer requiredSelection;

    @Column(name = "Sort_Order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;
}
