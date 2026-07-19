package com.sitech.prodai.repository;

import com.sitech.prodai.domain.entity.Scene;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SceneRepository extends JpaRepository<Scene, Integer> {

    Optional<Scene> findBySceneCode(String sceneCode);

    List<Scene> findByType(String type);

    List<Scene> findByParentId(Integer parentId);

    List<Scene> findByIsActiveTrue();
}
