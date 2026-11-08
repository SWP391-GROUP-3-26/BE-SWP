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

    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Integer id);

    @org.springframework.data.jpa.repository.Query("SELECT r FROM Room r WHERE " +
            "(:search IS NULL OR LOWER(r.name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:status IS NULL OR r.status = :status) AND (:type IS NULL OR r.type = :type) ORDER BY r.id DESC")
    List<Room> searchRooms(@org.springframework.data.repository.query.Param("search") String search,
                          @org.springframework.data.repository.query.Param("status") String status,
                          @org.springframework.data.repository.query.Param("type") com.swp391.beswp.entity.RoomType type);
}
