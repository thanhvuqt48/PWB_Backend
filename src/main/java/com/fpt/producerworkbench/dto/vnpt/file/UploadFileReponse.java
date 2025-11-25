package com.fpt.producerworkbench.dto.vnpt.file;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UploadFileReponse {

    private String message;
    private UploadFileObject object;
}
