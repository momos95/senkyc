package sn.senkyc.api.tenant.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sn.senkyc.api.platform.pagination.PageResult;
import sn.senkyc.api.tenant.application.CreateTenantCommand;
import sn.senkyc.api.tenant.application.TenantService;

@RestController
@RequestMapping("/api/v1")
class TenantController {

    private final TenantService tenantService;

    TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    /** Le tenant du jeton courant. */
    @GetMapping("/tenant")
    TenantResponse currentTenant() {
        return TenantResponse.from(tenantService.currentTenant());
    }

    /** Réservé aux administrateurs de plateforme (voir SecurityConfig). */
    @GetMapping("/admin/tenants")
    PageResult<TenantResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return tenantService.list(page, size).map(TenantResponse::from);
    }

    @PostMapping("/admin/tenants")
    ResponseEntity<TenantResponse> create(@Valid @RequestBody CreateTenantRequest request) {
        var tenant = tenantService.createTenant(
                new CreateTenantCommand(request.code(), request.name(), request.retentionDays()));
        return ResponseEntity.status(HttpStatus.CREATED).body(TenantResponse.from(tenant));
    }
}
