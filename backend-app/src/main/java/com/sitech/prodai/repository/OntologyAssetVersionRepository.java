package com.sitech.prodai.repository;

import com.sitech.prodai.domain.entity.OntologyAssetVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 本体资产版本库·主表仓库（P1-5 表 A）。
 */
public interface OntologyAssetVersionRepository extends JpaRepository<OntologyAssetVersion, Long> {

    List<OntologyAssetVersion> findByAssetTypeAndAssetCodeOrderByCreatedAtDesc(String assetType, String assetCode);

    Optional<OntologyAssetVersion> findByAssetTypeAndAssetCodeAndVersion(String assetType, String assetCode, String version);

    /** 最新发布版（last-known-good 事实源）。 */
    Optional<OntologyAssetVersion> findFirstByAssetTypeAndAssetCodeAndStatusOrderByPublishedAtDesc(
            String assetType, String assetCode, String status);

    List<OntologyAssetVersion> findByAssetTypeOrderByCreatedAtDesc(String assetType);

    Optional<OntologyAssetVersion> findFirstByAssetTypeAndStatusOrderByPublishedAtDesc(
            String assetType, String status);

    List<OntologyAssetVersion> findByAssetTypeAndStatus(String assetType, String status);

    List<OntologyAssetVersion> findByStatus(String status);
}
