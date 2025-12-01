package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.vnpt.classify.ClassifyResponse;
import com.fpt.producerworkbench.dto.vnpt.compare.CompareFaceResponse;
import com.fpt.producerworkbench.dto.vnpt.file.UploadFileResponse;
import com.fpt.producerworkbench.dto.vnpt.liveness.CardLivenessResponse;
import com.fpt.producerworkbench.dto.vnpt.liveness.FaceLivenessResponse;
import com.fpt.producerworkbench.dto.vnpt.ocrid.OcrIdReponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface VnptEkycService {

    String getAccessToken();

    UploadFileResponse uploadFile(MultipartFile file) throws IOException;
    
    OcrIdReponse ocrCccd(String frontHash, String backHash);
    
    ClassifyResponse classifyCccd(String imgCardHash);
    
    CompareFaceResponse compareFace(String imgFrontHash, String imgFaceHash);
    
    CardLivenessResponse liveness(String imgCardHash);
    
    FaceLivenessResponse faceLiveness(String imgHash);
}

