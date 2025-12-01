package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.common.AddendumDocumentType;
import com.fpt.producerworkbench.entity.AddendumDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface AddendumDocumentRepository extends JpaRepository<AddendumDocument, Long> {

    Optional<AddendumDocument> findFirstByAddendumIdAndTypeOrderByVersionDesc(Long addendumId, AddendumDocumentType type);

    Optional<AddendumDocument> findTopByAddendum_IdAndTypeOrderByVersionDesc(Long addendumId, AddendumDocumentType type);

    @Query("select coalesce(max(d.version),0) from AddendumDocument d " +
            "where d.addendum.id = :addendumId and d.type = :type")
    int findMaxVersion(Long addendumId, AddendumDocumentType type);
}

