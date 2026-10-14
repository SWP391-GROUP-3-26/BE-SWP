package com.swp391.beswp.repository;

import com.swp391.beswp.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomRepository extends JpaRepository<Room, Integer> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT r FROM Room r WHERE r.id = :id")
    java.util.Optional<Room> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Integer id);

    List<Room> findAllByOrderByNameAsc();
}
