package com.clinic.audit;

import com.clinic.common.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class AuditLogController {

    private final AuditService audit;

    public AuditLogController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    public PageResponse<AuditLogResponse> list(@RequestParam(required = false) String eventType,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return audit.list(eventType, page, size);
    }
}
