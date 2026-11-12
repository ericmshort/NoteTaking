package com.notetaking.repository;

import com.notetaking.entity.Notebook;
import com.notetaking.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotebookRepository extends JpaRepository<Notebook, Long> {
    List<Notebook> findByOwner(User owner);
}
