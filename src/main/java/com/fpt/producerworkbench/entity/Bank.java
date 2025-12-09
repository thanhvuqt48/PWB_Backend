package com.fpt.producerworkbench.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "banks")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Bank extends AbstractEntity<Long> {

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "short_name", length = 100)
    private String shortName;

    @Column(name = "bin", length = 20)
    private String bin;

    @Column(name = "logo_url", length = 1000)
    private String logoUrl;

    @Column(name = "transfer_supported")
    @Builder.Default
    private Boolean transferSupported = false;

    @Column(name = "lookup_supported")
    @Builder.Default
    private Boolean lookupSupported = false;

    @Column(name = "swift_code", length = 20)
    private String swiftCode;
}

