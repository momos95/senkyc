package sn.senkyc.api.tenant.adapter.in.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record CreateTenantRequest(
        @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,62}$", message = "must be 2-63 chars: a-z, 0-9, '-'")
                String code,
        @NotBlank @Size(max = 200) String name,
        @Min(0) @Max(3650) Integer retentionDays) {}
