package com.fpt.producerworkbench.dto.response;

import lombok.*;


@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowResponse {
private Long id;
private String firstName;
private String lastName;
private String fullName; 
private String avatarUrl;
private String location;
}
