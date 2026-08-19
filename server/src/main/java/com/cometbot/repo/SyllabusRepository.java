package com.cometbot.repo;

import com.cometbot.model.Syllabus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyllabusRepository extends JpaRepository<Syllabus, String> {
}