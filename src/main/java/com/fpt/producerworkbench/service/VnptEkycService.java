package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.vnpt.classify.ClassifyObject;
import com.fpt.producerworkbench.dto.vnpt.compare.CompareFaceObject;
import com.fpt.producerworkbench.dto.vnpt.file.UploadFileObject;
import com.fpt.producerworkbench.dto.vnpt.liveness.CardLivenessObject;
import com.fpt.producerworkbench.dto.vnpt.liveness.FaceLivenessObject;
import com.fpt.producerworkbench.dto.vnpt.ocrid.OcrIdObject;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface VnptEkycService {
    UploadFileObject uploadFile(MultipartFile file) throws IOException;
    
    OcrIdObject ocrCccd(String frontHash, String backHash);
    
    ClassifyObject classifyCccd(String imgCardHash);
    
    CompareFaceObject compareFace(String imgFrontHash, String imgFaceHash);
    
    CardLivenessObject liveness(String imgCardHash);
    
    FaceLivenessObject faceLiveness(String imgHash);
}

