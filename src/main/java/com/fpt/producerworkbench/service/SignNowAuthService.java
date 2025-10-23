package com.fpt.producerworkbench.service;

public interface SignNowAuthService {
    String getFromEmail();
    String getAccessToken();
    String forceRefresh();
}
