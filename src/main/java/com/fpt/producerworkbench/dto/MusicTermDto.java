package com.fpt.producerworkbench.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO representing a music term from music-terms.json
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MusicTermDto {
    
    /**
     * The music term (e.g., "DAW", "Reverb")
     */
    private String term;
    
    /**
     * Definition in Vietnamese
     */
    private String definition;
    
    /**
     * Examples of usage
     */
    private List<String> examples;
    
    /**
     * Synonyms or related terms
     */
    private List<String> synonyms;
    
    /**
     * Category (e.g., "foundations", "effects-processing", "mixing-mastering")
     */
    private String category;
}
