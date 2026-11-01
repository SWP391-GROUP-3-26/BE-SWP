package com.swp391.beswp.repository;

import com.swp391.beswp.entity.TrainingResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TrainingResultRepository extends JpaRepository<TrainingResult, Integer> {
    List<TrainingResult> findBySession_ClassEntity_IdOrderBySession_DateDescSession_EndTimeDescIdDesc(Integer classId);
    List<TrainingResult> findByMember_IdAndWithdrawnFalseOrderByUpdatedAtDesc(Integer memberId);
    Optional<TrainingResult> findBySession_IdAndMember_Id(Integer sessionId, Integer memberId);
    boolean existsBySession_IdAndMember_IdAndWithdrawnFalse(Integer sessionId, Integer memberId);
}
