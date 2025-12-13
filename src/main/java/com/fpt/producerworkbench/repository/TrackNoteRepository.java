package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.common.RoomType;
import com.fpt.producerworkbench.entity.TrackNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TrackNoteRepository extends JpaRepository<TrackNote, Long> {

    /**
     * Lấy tất cả notes của track theo roomType
     */
    @Query("SELECT n FROM TrackNote n WHERE n.track.id = :trackId AND n.roomType = :roomType ORDER BY n.createdAt DESC")
    List<TrackNote> findByTrackIdAndRoomType(@Param("trackId") Long trackId, @Param("roomType") RoomType roomType);

    /**
     * Lấy tất cả notes của track
     */
    @Query("SELECT n FROM TrackNote n WHERE n.track.id = :trackId ORDER BY n.createdAt DESC")
    List<TrackNote> findByTrackId(@Param("trackId") Long trackId);

    /**
     * Lấy note theo id và track id
     */
    @Query("SELECT n FROM TrackNote n WHERE n.id = :noteId AND n.track.id = :trackId")
    Optional<TrackNote> findByIdAndTrackId(@Param("noteId") Long noteId, @Param("trackId") Long trackId);

    /**
     * Xóa tất cả notes của track
     */
    void deleteByTrackId(Long trackId);

    /**
     * Đếm số notes của track
     */
    @Query("SELECT COUNT(n) FROM TrackNote n WHERE n.track.id = :trackId")
    long countByTrackId(@Param("trackId") Long trackId);
}
