package com.swp391.beswp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "Subject", schema = "dbo")
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Subject_ID")
    private Integer id;

    @Column(name = "Name", nullable = false, length = 100)
    private String name;

    @Column(name = "Description")
    private String description;

    @Column(name = "Category", length = 50)
    private String category;

    /** Active | Discontinued. Sport đã ngừng không được đưa vào Package mới. */
    @Column(name = "Status", nullable = false, length = 20)
    @Builder.Default
    private String status = "Active";

    @jakarta.persistence.ElementCollection
    @jakarta.persistence.CollectionTable(name = "Subject_Room_Type", schema = "dbo",
            joinColumns = @jakarta.persistence.JoinColumn(name = "Subject_ID"))
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "Room_Type", nullable = false, length = 30)
    @Builder.Default
    private java.util.Set<RoomType> allowedRoomTypes = new java.util.LinkedHashSet<>();
}
