package io.mosip.mimoto.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class IssuerConfigMapDTO {
    List<IssuerConfigDTO> issuers;
}
