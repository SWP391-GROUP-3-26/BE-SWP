package com.swp391.beswp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "Role", schema = "dbo")
public class Role {

    @Id
    @Column(name = "Role_id")
    private Integer id;

    @Column(name = "Role_Name")
    private String roleName;

    @Column(name = "Description")
    private String description;
}
