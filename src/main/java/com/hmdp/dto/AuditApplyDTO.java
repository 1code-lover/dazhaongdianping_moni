package com.hmdp.dto;

import lombok.Data;

/**
 * 审核申请DTO
 */
@Data
public class AuditApplyDTO {
    private Long applyId;
    private Integer status;
    private String rejectReason;
}
