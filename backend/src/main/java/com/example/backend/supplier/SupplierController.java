package com.example.backend.supplier;

import com.example.backend.supplier.dto.CreateSupplierRequest;
import com.example.backend.supplier.dto.SupplierResponse;
import com.example.backend.supplier.dto.UpdateSupplierRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
/**
 * HTTPリクエストを受けてユースケースを公開するコントローラ。
 */

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public List<SupplierResponse> getSuppliers() {
        return supplierService.getSuppliers();
    }

    @GetMapping("/{supplierId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public SupplierResponse getSupplier(@PathVariable Long supplierId) {
        return supplierService.getSupplier(supplierId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public SupplierResponse createSupplier(@Valid @RequestBody CreateSupplierRequest request) {
        return supplierService.createSupplier(request);
    }

    @PutMapping("/{supplierId}")
    @PreAuthorize("hasRole('ADMIN')")
    public SupplierResponse updateSupplier(@PathVariable Long supplierId, @Valid @RequestBody UpdateSupplierRequest request) {
        return supplierService.updateSupplier(supplierId, request);
    }

    @PostMapping("/{supplierId}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public SupplierResponse activateSupplier(@PathVariable Long supplierId) {
        return supplierService.activateSupplier(supplierId);
    }

    @PostMapping("/{supplierId}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public SupplierResponse deactivateSupplier(@PathVariable Long supplierId) {
        return supplierService.deactivateSupplier(supplierId);
    }
}
