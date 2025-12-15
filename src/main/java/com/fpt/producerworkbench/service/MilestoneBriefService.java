package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.MilestoneBriefGroupRequest;
import com.fpt.producerworkbench.dto.request.MilestoneBriefUpsertRequest;
import com.fpt.producerworkbench.dto.response.MilestoneBriefDetailResponse;
import com.fpt.producerworkbench.dto.response.MilestoneBriefGroupResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface MilestoneBriefService {

    String getBriefFileUrl(Long projectId, Long milestoneId, String fileKey, Authentication auth);

    String uploadBriefFile(Long projectId, Long milestoneId, MultipartFile file, String type, Authentication auth);

    MilestoneBriefDetailResponse getMilestoneBrief(Long projectId, Long milestoneId, Authentication auth);

    List<MilestoneBriefGroupResponse> createBriefGroups(Long projectId, Long milestoneId, MilestoneBriefUpsertRequest request, Authentication auth);

    MilestoneBriefDetailResponse upsertMilestoneBrief(Long projectId, Long milestoneId, MilestoneBriefUpsertRequest request, Authentication auth);

    void deleteBriefGroup(Long projectId, Long milestoneId, Long groupId, Authentication auth);

    MilestoneBriefDetailResponse getInternalMilestoneBrief(Long projectId, Long milestoneId, Authentication auth);

    MilestoneBriefDetailResponse upsertInternalMilestoneBrief(Long projectId, Long milestoneId, MilestoneBriefUpsertRequest request, Authentication auth);

    void deleteInternalBriefGroup(Long projectId, Long milestoneId, Long groupId, Authentication auth);

    MilestoneBriefGroupResponse forwardExternalGroupToInternal(Long projectId, Long milestoneId, Long groupId, Authentication auth);

    /**
     * Chuyển tiếp group từ EXTERNAL sang INTERNAL với tùy chọn chỉnh sửa
     * Chỉ Owner mới có quyền thực hiện
     *
     * @param projectId ID của project
     * @param milestoneId ID của milestone
     * @param groupId ID của group cần chuyển tiếp
     * @param request Nội dung group đã chỉnh sửa (optional, nếu null sẽ forward nguyên bản)
     * @param auth Authentication
     * @return MilestoneBriefGroupResponse group mới ở INTERNAL
     */
    MilestoneBriefGroupResponse forwardExternalGroupToInternalWithEdit(
            Long projectId, 
            Long milestoneId, 
            Long groupId, 
            com.fpt.producerworkbench.dto.request.ForwardBriefGroupRequest request,
            Authentication auth);
}