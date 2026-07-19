package com.sitech.prodai.repository;

import com.sitech.prodai.domain.entity.Prompt;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PromptRepository extends JpaRepository<Prompt, Integer> {

    @Query("SELECT p FROM Prompt p WHERE p.code = :code")
    Optional<Prompt> findByCode(@Param("code") String code);

    @Query("SELECT p FROM Prompt p WHERE p.category = :category")
    List<Prompt> findByCategory(@Param("category") String category);

    @Query("SELECT p FROM Prompt p WHERE p.category = :category")
    List<Prompt> findByCategory(@Param("category") String category, Sort sort);

    @Query("SELECT p FROM Prompt p WHERE p.isActive = true")
    List<Prompt> findByIsActiveTrue();

    @Query("SELECT p FROM Prompt p WHERE p.isActive = :isActive")
    List<Prompt> findByIsActive(@Param("isActive") Boolean isActive);

    @Query("SELECT p FROM Prompt p WHERE p.isActive = :isActive")
    List<Prompt> findByIsActive(@Param("isActive") Boolean isActive, Sort sort);

    @Query("SELECT p FROM Prompt p WHERE p.category = :category AND p.isActive = true")
    List<Prompt> findByCategoryAndIsActiveTrue(@Param("category") String category);

    @Query("SELECT p FROM Prompt p WHERE p.category = :category AND p.isActive = :isActive")
    List<Prompt> findByCategoryAndIsActive(@Param("category") String category, @Param("isActive") Boolean isActive);

    @Query("SELECT p FROM Prompt p WHERE p.category = :category AND p.isActive = :isActive")
    List<Prompt> findByCategoryAndIsActive(@Param("category") String category, @Param("isActive") Boolean isActive, Sort sort);

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Prompt p WHERE p.code = :code")
    boolean existsByCode(@Param("code") String code);
}
