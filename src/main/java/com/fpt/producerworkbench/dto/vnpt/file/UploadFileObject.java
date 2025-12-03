package com.fpt.producerworkbench.dto.vnpt.file;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UploadFileObject implements Serializable {

    private String fileName;
    private String title;
    private String description;
    private String hash;
    private String fileType;
    private String uploadedDate;
    private String storageType;
    private String tokenId;
}
