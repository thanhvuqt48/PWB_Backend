package com.fpt.producerworkbench.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MusicTermDto {
    private String term;
    private String definition;
    private List<String> examples;
    private List<String> synonyms;
    private String category;
    
    @JsonProperty("related_terms")
    private List<String> relatedTerms;
    
    @JsonProperty("usage_context")
    private String usageContext;
}
