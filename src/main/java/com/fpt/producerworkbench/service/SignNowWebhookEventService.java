package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.entity.Contract;
import com.fpt.producerworkbench.entity.ContractAddendum;
import org.springframework.http.ResponseEntity;

/**
 * Service xử lý các events từ SignNow webhook
 */
public interface SignNowWebhookEventService {
    
    /**
     * Xử lý webhook event cho Contract
     * 
     * @param contract Contract entity
     * @param documentId SignNow document ID
     * @param event Event type từ SignNow
     * @param isFieldInviteComplete true nếu là document.fieldinvite.complete
     * @param isDocumentComplete true nếu là document.complete
     * @param isSignedEvent true nếu là event signed
     * @return ResponseEntity với kết quả xử lý
     */
    ResponseEntity<String> handleContractEvent(
            Contract contract,
            String documentId,
            String event,
            boolean isFieldInviteComplete,
            boolean isDocumentComplete,
            boolean isSignedEvent
    );

    /**
     * Xử lý webhook event cho Addendum
     * 
     * @param addendum ContractAddendum entity
     * @param documentId SignNow document ID
     * @param event Event type từ SignNow
     * @param isFieldInviteComplete true nếu là document.fieldinvite.complete
     * @param isDocumentComplete true nếu là document.complete
     * @param isSignedEvent true nếu là event signed
     * @return ResponseEntity với kết quả xử lý
     */
    ResponseEntity<String> handleAddendumEvent(
            ContractAddendum addendum,
            String documentId,
            String event,
            boolean isFieldInviteComplete,
            boolean isDocumentComplete,
            boolean isSignedEvent
    );
}

