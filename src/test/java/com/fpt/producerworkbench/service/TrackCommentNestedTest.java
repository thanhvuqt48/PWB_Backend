package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.common.CommentStatus;
import com.fpt.producerworkbench.dto.request.TrackCommentCreateRequest;
import com.fpt.producerworkbench.dto.response.TrackCommentResponse;
import com.fpt.producerworkbench.entity.Track;
import com.fpt.producerworkbench.entity.TrackComment;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.repository.TrackCommentRepository;
import com.fpt.producerworkbench.repository.TrackMilestoneRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.impl.TrackCommentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test case để verify nested comments (reply của reply) hoạt động đúng
 * Giống cấu trúc comment của Facebook
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Track Comment Nested Replies Test")
class TrackCommentNestedTest {

    @Mock
    private TrackCommentRepository trackCommentRepository;

    @Mock
    private TrackMilestoneRepository trackRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private TrackCommentServiceImpl trackCommentService;

    @Mock
    private Authentication authentication;

    private User testUser;
    private Track testTrack;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("test@example.com")
                .firstName("John")
                .lastName("Doe")
                .build();
        testUser.setId(1L);

        testTrack = Track.builder()
                .user(testUser)
                .name("Test Track")
                .duration(180)
                .build();
        testTrack.setId(1L);
    }

    @Test
    @DisplayName("Should support nested comments - Level 3 deep (Comment -> Reply -> Reply of Reply)")
    void testNestedCommentsLevel3() {
        // Arrange: Tạo cấu trúc comment 3 cấp
        // Level 1: Comment gốc
        TrackComment rootComment = TrackComment.builder()
                .track(testTrack)
                .user(testUser)
                .content("This is root comment")
                .timestamp(45)
                .status(CommentStatus.PENDING)
                .parentComment(null)
                .isDeleted(false)
                .build();
        rootComment.setId(1L);

        // Level 2: Reply của root comment
        TrackComment reply1 = TrackComment.builder()
                .track(testTrack)
                .user(testUser)
                .content("This is a reply to root comment")
                .parentComment(rootComment)
                .status(CommentStatus.PENDING)
                .isDeleted(false)
                .build();
        reply1.setId(2L);

        // Level 3: Reply của reply (nested)
        TrackComment reply2 = TrackComment.builder()
                .track(testTrack)
                .user(testUser)
                .content("This is a reply to reply (nested)")
                .parentComment(reply1)
                .status(CommentStatus.PENDING)
                .isDeleted(false)
                .build();
        reply2.setId(3L);

        // Mock repository responses
        when(authentication.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(trackCommentRepository.findByIdAndNotDeleted(1L)).thenReturn(Optional.of(rootComment));
        
        // Root comment có 1 reply
        when(trackCommentRepository.countRepliesByParentCommentId(1L)).thenReturn(1L);
        when(trackCommentRepository.findRepliesByParentCommentId(1L)).thenReturn(Arrays.asList(reply1));
        
        // Reply1 có 1 reply (nested)
        when(trackCommentRepository.countRepliesByParentCommentId(2L)).thenReturn(1L);
        when(trackCommentRepository.findRepliesByParentCommentId(2L)).thenReturn(Arrays.asList(reply2));
        
        // Reply2 không có reply
        when(trackCommentRepository.countRepliesByParentCommentId(3L)).thenReturn(0L);

        // Act: Lấy comment với replies nested
        TrackCommentResponse response = trackCommentService.getCommentById(authentication, 1L);

        // Assert: Verify cấu trúc nested
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getContent()).isEqualTo("This is root comment");
        assertThat(response.getReplyCount()).isEqualTo(1L);
        assertThat(response.getReplies()).isNotNull();
        assertThat(response.getReplies()).hasSize(1);

        // Verify Level 2 (reply)
        TrackCommentResponse level2Reply = response.getReplies().get(0);
        assertThat(level2Reply.getId()).isEqualTo(2L);
        assertThat(level2Reply.getContent()).isEqualTo("This is a reply to root comment");
        assertThat(level2Reply.getParentCommentId()).isEqualTo(1L);
        assertThat(level2Reply.getReplyCount()).isEqualTo(1L);
        assertThat(level2Reply.getReplies()).isNotNull();
        assertThat(level2Reply.getReplies()).hasSize(1);

        // Verify Level 3 (reply of reply) - NESTED
        TrackCommentResponse level3Reply = level2Reply.getReplies().get(0);
        assertThat(level3Reply.getId()).isEqualTo(3L);
        assertThat(level3Reply.getContent()).isEqualTo("This is a reply to reply (nested)");
        assertThat(level3Reply.getParentCommentId()).isEqualTo(2L);
        assertThat(level3Reply.getReplyCount()).isEqualTo(0L);
        assertThat(level3Reply.getReplies()).isNull(); // Không có reply nữa

        System.out.println("✅ Test passed: Nested comments work correctly!");
    }

    @Test
    @DisplayName("Should support creating reply to reply (nested reply)")
    void testCreateNestedReply() {
        // Arrange: Tạo reply cho một reply (không phải root comment)
        TrackComment parentOfParent = TrackComment.builder().build();
        parentOfParent.setId(1L);
        
        TrackComment parentReply = TrackComment.builder()
                .track(testTrack)
                .user(testUser)
                .content("Parent reply")
                .parentComment(parentOfParent) // Có parent = đây là reply
                .status(CommentStatus.PENDING)
                .isDeleted(false)
                .build();
        parentReply.setId(2L);

        TrackCommentCreateRequest request = TrackCommentCreateRequest.builder()
                .content("Reply to a reply")
                .parentCommentId(2L) // Reply cho comment 2 (đã là reply rồi)
                .build();

        // Mock
        when(authentication.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(trackRepository.findById(1L)).thenReturn(Optional.of(testTrack));
        when(trackCommentRepository.findByIdAndNotDeleted(2L)).thenReturn(Optional.of(parentReply));
        when(trackCommentRepository.save(any(TrackComment.class))).thenAnswer(invocation -> {
            TrackComment saved = invocation.getArgument(0);
            saved.setId(3L); // Set ID sau khi save
            return saved;
        });
        when(trackCommentRepository.countRepliesByParentCommentId(3L)).thenReturn(0L);

        // Act: Tạo nested reply
        TrackCommentResponse response = trackCommentService.createComment(authentication, 1L, request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getContent()).isEqualTo("Reply to a reply");
        assertThat(response.getParentCommentId()).isEqualTo(2L); // Parent là reply, không phải root
        
        // Verify save được gọi
        verify(trackCommentRepository, times(1)).save(any(TrackComment.class));
        
        System.out.println("✅ Test passed: Can create reply to reply (nested)!");
    }

    @Test
    @DisplayName("Should get replies with nested structure when calling getRepliesByComment")
    void testGetRepliesWithNestedStructure() {
        // Arrange
        TrackComment parentComment = TrackComment.builder().build();
        parentComment.setId(1L);
        
        TrackComment reply1 = TrackComment.builder()
                .track(testTrack)
                .user(testUser)
                .content("Reply 1")
                .parentComment(parentComment)
                .status(CommentStatus.PENDING)
                .isDeleted(false)
                .build();
        reply1.setId(2L);

        TrackComment nestedReply = TrackComment.builder()
                .track(testTrack)
                .user(testUser)
                .content("Nested reply")
                .parentComment(reply1)
                .status(CommentStatus.PENDING)
                .isDeleted(false)
                .build();
        nestedReply.setId(3L);

        when(authentication.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(trackCommentRepository.findByIdAndNotDeleted(1L)).thenReturn(Optional.of(parentComment));
        when(trackCommentRepository.findRepliesByParentCommentId(1L)).thenReturn(Arrays.asList(reply1));
        when(trackCommentRepository.countRepliesByParentCommentId(2L)).thenReturn(1L);
        when(trackCommentRepository.findRepliesByParentCommentId(2L)).thenReturn(Arrays.asList(nestedReply));
        when(trackCommentRepository.countRepliesByParentCommentId(3L)).thenReturn(0L);

        // Act
        List<TrackCommentResponse> replies = trackCommentService.getRepliesByComment(authentication, 1L);

        // Assert
        assertThat(replies).isNotNull();
        assertThat(replies).hasSize(1);
        
        TrackCommentResponse firstReply = replies.get(0);
        assertThat(firstReply.getReplies()).isNotNull();
        assertThat(firstReply.getReplies()).hasSize(1);
        assertThat(firstReply.getReplies().get(0).getContent()).isEqualTo("Nested reply");

        System.out.println("✅ Test passed: getRepliesByComment returns nested structure!");
    }
}

