//package com.fpt.producerworkbench.configuration;
//
//import org.springframework.boot.context.properties.ConfigurationProperties;
//import java.util.ArrayList;
//import java.util.List;
//
//@ConfigurationProperties(prefix = "gcp")
//public class GcpProperties {
//
//    private String projectId;
//    private String credentialsFile;
//    private Gcs gcs = new Gcs();
//    private Speech speech = new Speech();
//
//    public String getProjectId() { return projectId; }
//    public void setProjectId(String projectId) { this.projectId = projectId; }
//
//    public String getCredentialsFile() { return credentialsFile; }
//    public void setCredentialsFile(String credentialsFile) { this.credentialsFile = credentialsFile; }
//
//    public Gcs getGcs() { return gcs; }
//    public void setGcs(Gcs gcs) { this.gcs = gcs; }
//
//    public Speech getSpeech() { return speech; }
//    public void setSpeech(Speech speech) { this.speech = speech; }
//
//    public static class Gcs {
//        private String bucket;
//        private boolean deleteAfterFinish = true;
//
//        public String getBucket() { return bucket; }
//        public void setBucket(String bucket) { this.bucket = bucket; }
//
//        public boolean isDeleteAfterFinish() { return deleteAfterFinish; }
//        public void setDeleteAfterFinish(boolean deleteAfterFinish) { this.deleteAfterFinish = deleteAfterFinish; }
//    }
//
//    public static class Speech {
//        private String languageCode = "vi-VN";
//        private boolean enablePunctuation = true;
//        private String model = "latest_long";
//        private int timeoutSeconds = 1200;
//        private boolean useEnhanced = false;
//        private List<String> alternativeLanguageCodes = new ArrayList<>();
//        private String location = "global";
//
//        public String getLanguageCode() { return languageCode; }
//        public void setLanguageCode(String languageCode) { this.languageCode = languageCode; }
//
//        public boolean isEnablePunctuation() { return enablePunctuation; }
//        public Boolean getEnablePunctuation() { return enablePunctuation; }
//        public void setEnablePunctuation(boolean enablePunctuation) { this.enablePunctuation = enablePunctuation; }
//
//        public String getModel() { return model; }
//        public void setModel(String model) { this.model = model; }
//
//        public int getTimeoutSeconds() { return timeoutSeconds; }
//        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
//
//        public boolean isUseEnhanced() { return useEnhanced; }
//        public void setUseEnhanced(boolean useEnhanced) { this.useEnhanced = useEnhanced; }
//
//        public List<String> getAlternativeLanguageCodes() { return alternativeLanguageCodes; }
//        public void setAlternativeLanguageCodes(List<String> alternativeLanguageCodes) {
//            this.alternativeLanguageCodes = alternativeLanguageCodes;
//        }
//
//        public String getLocation() { return location; }
//        public void setLocation(String location) { this.location = location; }
//    }
//}
package com.fpt.producerworkbench.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "gcp")
public class GcpProperties {

    private String projectId;
    private String credentialsFile;
    private Gcs gcs = new Gcs();
    private Speech speech = new Speech();

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getCredentialsFile() { return credentialsFile; }
    public void setCredentialsFile(String credentialsFile) { this.credentialsFile = credentialsFile; }

    public Gcs getGcs() { return gcs; }
    public void setGcs(Gcs gcs) { this.gcs = gcs; }

    public Speech getSpeech() { return speech; }
    public void setSpeech(Speech speech) { this.speech = speech; }

    public static class Gcs {
        private String bucket;                 // staging bucket (input)
        private boolean deleteAfterFinish = true;

        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }

        public boolean isDeleteAfterFinish() { return deleteAfterFinish; }
        public void setDeleteAfterFinish(boolean deleteAfterFinish) { this.deleteAfterFinish = deleteAfterFinish; }
    }

    public static class Speech {
        private String languageCode = "vi-VN";
        private boolean enablePunctuation = true;
        private String model = "latest_long";
        private int timeoutSeconds = 1200;
        private boolean useEnhanced = false; // v2 thường không dùng
        private List<String> alternativeLanguageCodes = new ArrayList<>();

        private String location = "global";
        private String gcsOutputBucket = "";
        private String gcsOutputPrefix = "pwb/stt/";

        public String getLanguageCode() { return languageCode; }
        public void setLanguageCode(String languageCode) { this.languageCode = languageCode; }

        public boolean isEnablePunctuation() { return enablePunctuation; }
        public Boolean getEnablePunctuation() { return enablePunctuation; }
        public void setEnablePunctuation(boolean enablePunctuation) { this.enablePunctuation = enablePunctuation; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

        public boolean isUseEnhanced() { return useEnhanced; }
        public void setUseEnhanced(boolean useEnhanced) { this.useEnhanced = useEnhanced; }

        public List<String> getAlternativeLanguageCodes() { return alternativeLanguageCodes; }
        public void setAlternativeLanguageCodes(List<String> alternativeLanguageCodes) {
            this.alternativeLanguageCodes = alternativeLanguageCodes;
        }

        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }

        public String getGcsOutputBucket() { return gcsOutputBucket; }
        public void setGcsOutputBucket(String gcsOutputBucket) { this.gcsOutputBucket = gcsOutputBucket; }

        public String getGcsOutputPrefix() { return gcsOutputPrefix; }
        public void setGcsOutputPrefix(String gcsOutputPrefix) { this.gcsOutputPrefix = gcsOutputPrefix; }
    }
}
