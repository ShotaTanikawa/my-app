package com.example.backend.supplier;

import com.example.backend.audit.AuditLogService;
import com.example.backend.common.BusinessRuleException;
import com.example.backend.common.ResourceNotFoundException;
import com.example.backend.supplier.dto.CreateSupplierRequest;
import com.example.backend.supplier.dto.SupplierResponse;
import com.example.backend.supplier.dto.UpdateSupplierRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final AuditLogService auditLogService;

    public SupplierService(
            SupplierRepository supplierRepository,
            AuditLogService auditLogService
    ) {
        this.supplierRepository = supplierRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<SupplierResponse> getSuppliers() {
        return supplierRepository.findAllByOrderByActiveDescNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SupplierResponse getSupplier(Long supplierId) {
        return toResponse(findSupplierById(supplierId));
    }

    @Transactional
    public SupplierResponse createSupplier(CreateSupplierRequest request) {
        String normalizedCode = normalizeRequired(request.code());
        if (supplierRepository.existsByCode(normalizedCode)) {
            throw new BusinessRuleException("Supplier code already exists: " + normalizedCode);
        }

        Supplier supplier = new Supplier();
        supplier.setCode(normalizedCode);
        supplier.setName(normalizeRequired(request.name()));
        supplier.setContactName(normalizeOptional(request.contactName()));
        supplier.setEmail(normalizeOptional(request.email()));
        supplier.setPhone(normalizeOptional(request.phone()));
        supplier.setNote(normalizeOptional(request.note()));
        supplier.setActive(true);

        Supplier saved = supplierRepository.save(supplier);
        auditLogService.log(
                "SUPPLIER_CREATE",
                "SUPPLIER",
                saved.getId().toString(),
                "code=" + saved.getCode() + ", name=" + saved.getName()
        );
        return toResponse(saved);
    }

    @Transactional
    public SupplierResponse updateSupplier(Long supplierId, UpdateSupplierRequest request) {
        Supplier supplier = findSupplierById(supplierId);
        String normalizedCode = normalizeRequired(request.code());

        if (supplierRepository.existsByCodeAndIdNot(normalizedCode, supplierId)) {
            throw new BusinessRuleException("Supplier code already exists: " + normalizedCode);
        }

        supplier.setCode(normalizedCode);
        supplier.setName(normalizeRequired(request.name()));
        supplier.setContactName(normalizeOptional(request.contactName()));
        supplier.setEmail(normalizeOptional(request.email()));
        supplier.setPhone(normalizeOptional(request.phone()));
        supplier.setNote(normalizeOptional(request.note()));
        if (request.active() != null) {
            supplier.setActive(request.active());
        }

        auditLogService.log(
                "SUPPLIER_UPDATE",
                "SUPPLIER",
                supplier.getId().toString(),
                "code=" + supplier.getCode() + ", name=" + supplier.getName() + ", active=" + supplier.getActive()
        );
        return toResponse(supplier);
    }

    @Transactional
    public SupplierResponse activateSupplier(Long supplierId) {
        Supplier supplier = findSupplierById(supplierId);
        supplier.setActive(true);
        auditLogService.log(
                "SUPPLIER_ACTIVATE",
                "SUPPLIER",
                supplier.getId().toString(),
                "code=" + supplier.getCode()
        );
        return toResponse(supplier);
    }

    @Transactional
    public SupplierResponse deactivateSupplier(Long supplierId) {
        Supplier supplier = findSupplierById(supplierId);
        supplier.setActive(false);
        auditLogService.log(
                "SUPPLIER_DEACTIVATE",
                "SUPPLIER",
                supplier.getId().toString(),
                "code=" + supplier.getCode()
        );
        return toResponse(supplier);
    }

    private Supplier findSupplierById(Long supplierId) {
        return supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found: " + supplierId));
    }

    private String normalizeRequired(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private SupplierResponse toResponse(Supplier supplier) {
        return new SupplierResponse(
                supplier.getId(),
                supplier.getCode(),
                supplier.getName(),
                supplier.getContactName(),
                supplier.getEmail(),
                supplier.getPhone(),
                supplier.getNote(),
                supplier.getActive()
        );
    }
}
