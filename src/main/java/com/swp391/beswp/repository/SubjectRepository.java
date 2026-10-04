package com.swp391.beswp.repository;

import com.swp391.beswp.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, Integer> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Integer id);

    @Query("SELECT s FROM Subject s WHERE " +
           "(:keyword IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(s.description) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR (:parsedId IS NOT NULL AND s.id = :parsedId)) " +
           "AND (:category IS NULL OR LOWER(s.category) = LOWER(:category)) " +
           "ORDER BY s.id ASC")
    List<Subject> searchSubjects(
            @Param("keyword") String keyword,
            @Param("parsedId") Integer parsedId,
            @Param("category") String category
    );
}
