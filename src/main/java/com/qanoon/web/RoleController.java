package com.qanoon.web;

import com.qanoon.common.*;
import com.qanoon.domain.Permission;
import com.qanoon.domain.Role;
import com.qanoon.repo.RoleRepository;
import com.qanoon.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** الأدوار وصلاحياتها الدقيقة — تشمل إنشاء أدوار مخصصة كالسكرتير وموظف الإدخال. */
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final SecurityUtils securityUtils;
    private final AuditService auditService;

    public record PermissionOption(String code, String label) {}

    @GetMapping
    public List<Role> list() {
        securityUtils.require(Permission.USERS_MANAGE);
        return roleRepository.findAll();
    }

    /** كل الصلاحيات المتاحة بعناوينها العربية لبناء شبكة الاختيار. */
    @GetMapping("/permissions")
    public List<PermissionOption> permissions() {
        securityUtils.require(Permission.USERS_MANAGE);
        return Permission.LABELS.entrySet().stream()
                .map(e -> new PermissionOption(e.getKey(), e.getValue()))
                .toList();
    }

    @PostMapping
    public ApiResponse create(@RequestBody Role in) {
        securityUtils.require(Permission.USERS_MANAGE);
        validate(in);
        if (roleRepository.findByCode(in.getCode().trim()).isPresent()) {
            throw new BusinessException("يوجد دور بنفس الرمز: " + in.getCode());
        }
        Role r = new Role();
        r.setCode(in.getCode().trim().toUpperCase());
        r.setNameAr(in.getNameAr().trim());
        r.setDescription(in.getDescription());
        r.setSystem(false);
        r.setPermissions(clean(in));
        Role saved = roleRepository.save(r);
        auditService.log("CREATE", "Role", saved.getId(), saved.getCode(),
                "إنشاء دور مخصص: " + saved.getNameAr() + " بعدد " + saved.getPermissions().size() + " صلاحية");
        return ApiResponse.ok("تم إنشاء الدور", saved);
    }

    @PutMapping("/{id}")
    public ApiResponse update(@PathVariable Long id, @RequestBody Role in) {
        securityUtils.require(Permission.USERS_MANAGE);
        Role r = roleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("الدور غير موجود"));
        if (r.isSystem()) {
            throw new BusinessException("لا يجوز تعديل صلاحيات دور أساسي — أنشئ دوراً مخصصاً بدلاً من ذلك");
        }
        if (in.getNameAr() != null && !in.getNameAr().isBlank()) {
            r.setNameAr(in.getNameAr().trim());
        }
        r.setDescription(in.getDescription());
        r.setPermissions(clean(in));
        Role saved = roleRepository.save(r);
        auditService.log("UPDATE", "Role", saved.getId(), saved.getCode(),
                "تعديل الدور: " + saved.getNameAr());
        return ApiResponse.ok("تم حفظ الدور", saved);
    }

    @DeleteMapping("/{id}")
    public ApiResponse delete(@PathVariable Long id) {
        securityUtils.require(Permission.USERS_MANAGE);
        Role r = roleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("الدور غير موجود"));
        if (r.isSystem()) {
            throw new BusinessException("لا يجوز حذف دور أساسي");
        }
        long linked = userRepository.findAll().stream()
                .filter(u -> u.getRole() != null && id.equals(u.getRole().getId()))
                .count();
        if (linked > 0) {
            throw new BusinessException("لا يمكن حذف الدور — مرتبط بـ " + linked + " مستخدم");
        }
        roleRepository.delete(r);
        auditService.log("DELETE", "Role", id, r.getCode(), "حذف الدور: " + r.getNameAr());
        return ApiResponse.ok("تم حذف الدور");
    }

    private static void validate(Role in) {
        if (in.getCode() == null || in.getCode().isBlank()) {
            throw new BusinessException("رمز الدور مطلوب");
        }
        if (in.getNameAr() == null || in.getNameAr().isBlank()) {
            throw new BusinessException("اسم الدور بالعربية مطلوب");
        }
    }

    /** يقبل فقط الصلاحيات المعروفة في النظام. */
    private static java.util.Set<String> clean(Role in) {
        var set = new LinkedHashSet<String>();
        if (in.getPermissions() != null) {
            for (String p : in.getPermissions()) {
                if (p != null && Permission.LABELS.containsKey(p.trim())) {
                    set.add(p.trim());
                }
            }
        }
        if (set.isEmpty()) {
            throw new BusinessException("يجب اختيار صلاحية واحدة على الأقل");
        }
        return set;
    }

    /** مساعد للواجهة: عدد المستخدمين لكل دور. */
    @GetMapping("/usage")
    public Map<String, Long> usage() {
        securityUtils.require(Permission.USERS_MANAGE);
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        u -> u.getRole().getCode(), java.util.stream.Collectors.counting()));
    }
}
