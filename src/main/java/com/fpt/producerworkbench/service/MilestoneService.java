package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.common.MilestoneChatType;
import com.fpt.producerworkbench.dto.request.AddMilestoneMemberRequest;
import com.fpt.producerworkbench.dto.request.CreateMilestoneGroupChatRequest;
import com.fpt.producerworkbench.dto.request.MilestoneRequest;
import com.fpt.producerworkbench.dto.response.AvailableProjectMemberResponse;
import com.fpt.producerworkbench.dto.response.ConversationCreationResponse;
import com.fpt.producerworkbench.dto.response.MilestoneListResponse;
import com.fpt.producerworkbench.dto.response.MilestoneResponse;
import com.fpt.producerworkbench.dto.response.MilestoneDetailResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface MilestoneService {

    List<MilestoneListResponse> getAllMilestonesByProject(Long projectId, Authentication auth);

    MilestoneResponse createMilestone(Long projectId, MilestoneRequest request, Authentication auth);

    MilestoneResponse updateMilestone(Long projectId, Long milestoneId, MilestoneRequest request, Authentication auth);

    MilestoneDetailResponse getMilestoneDetail(Long projectId, Long milestoneId, Authentication auth);

    List<AvailableProjectMemberResponse> getAvailableProjectMembers(Long projectId, Long milestoneId, Authentication auth);

    MilestoneDetailResponse addMembersToMilestone(Long projectId, Long milestoneId, AddMilestoneMemberRequest request, Authentication auth);

    void deleteMilestone(Long projectId, Long milestoneId, Authentication auth);

    MilestoneDetailResponse removeMemberFromMilestone(Long projectId, Long milestoneId, Long userId, Authentication auth);

    ConversationCreationResponse createGroupChatForMilestone(Long projectId, Long milestoneId, CreateMilestoneGroupChatRequest request, MultipartFile avatar, Authentication auth);

    List<ConversationCreationResponse> getGroupChatsForMilestone(Long projectId, Long milestoneId, MilestoneChatType type, Authentication auth);

    List<AvailableProjectMemberResponse> searchUsersForMilestoneChat(Long projectId, Long milestoneId, String keyword, Authentication auth);

    MilestoneResponse completeMilestone(Long projectId, Long milestoneId, Authentication auth);

    com.fpt.producerworkbench.dto.response.DownloadOriginalTracksZipResponse downloadOriginalTracksZip(
            Long projectId, Long milestoneId,
            com.fpt.producerworkbench.dto.request.DownloadOriginalTracksZipRequest request,
            Authentication auth);
}

