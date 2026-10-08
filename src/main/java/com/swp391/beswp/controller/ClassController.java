package com.swp391.beswp.controller;

import com.swp391.beswp.dto.ClassFormOptionsResponse;
import com.swp391.beswp.dto.ClassListResponse;
import com.swp391.beswp.dto.ClassResponse;
import com.swp391.beswp.dto.CreateClassRequest;
import com.swp391.beswp.service.ClassService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/classes")
@RequiredArgsConstructor
public class ClassController {

    private final ClassService classService;

    @PostMapping
    public ResponseEntity<ClassResponse> createClass(@Valid @RequestBody CreateClassRequest request) {
        ClassResponse response = classService.createClass(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<ClassListResponse> getAllClasses(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(classService.getAllClasses(search, status));
    }

    @GetMapping("/options")
    public ResponseEntity<ClassFormOptionsResponse> getFormOptions() {
        return ResponseEntity.ok(classService.getFormOptions());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClassResponse> getClassById(@PathVariable Integer id) {
        return ResponseEntity.ok(classService.getClassById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteClass(@PathVariable Integer id) {
        classService.deleteClass(id);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Xóa lớp học thành công"
        ));
    }
}
