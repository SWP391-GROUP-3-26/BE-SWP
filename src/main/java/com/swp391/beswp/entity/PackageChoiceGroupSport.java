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
 * Sport có thể được chọn trong một Choice Group.
 * Member phải chọn chính xác {@code PackageChoiceGroup#requiredSelection} Sport
 * từ tập hợp các record này của một Group.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "Package_Choice_Group_Sport", schema = "dbo")
public class PackageChoiceGroupSport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Choice_Group_ID", nullable = false)
    private PackageChoiceGroup choiceGroup;

    /**
     * Sport (Subject) có thể được Member lựa chọn trong Group này.
     * Sport DISCONTINUED không được thêm vào Group của Package mới.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Subject_ID", nullable = false)
    private Subject subject;
}
