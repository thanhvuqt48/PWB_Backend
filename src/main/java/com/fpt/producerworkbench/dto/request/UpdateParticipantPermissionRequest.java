package com.fpt.producerworkbench.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateParticipantPermissionRequest {

    private Boolean canShareAudio;

    private Boolean canShareVideo;

    private Boolean canControlPlayback;

    private Boolean canApproveFiles;
}
