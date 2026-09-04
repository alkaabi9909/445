package com.qanoon.web;

import com.qanoon.common.SecurityUtils;
import com.qanoon.domain.*;
import com.qanoon.repo.PartyRepository;
import com.qanoon.repo.RoleRepository;
import com.qanoon.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/** كل القوائم المرجعية التي تحتاجها الواجهة، تُحمَّل مرة واحدة بعد الدخول. */
@RestController
@RequestMapping("/api/lookups")
@RequiredArgsConstructor
public class LookupController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PartyRepository partyRepository;
    private final SecurityUtils securityUtils;

    public record Option(String value, String label) {}

    public record PersonOption(Long id, String name, String specialization) {}

    @GetMapping
    public Map<String, Object> lookups() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fileStatus", of(Enums.FileStatus.values()));
        m.put("closureType", of(Enums.ClosureType.values()));
        m.put("commType", of(Enums.CommType.values()));
        m.put("paymentMethod", of(Enums.PaymentMethod.values()));
        m.put("paymentStatus", of(Enums.PaymentStatus.values()));
        m.put("installmentStatus", of(Enums.InstallmentStatus.values()));
        m.put("caseStatus", of(Enums.CaseStatus.values()));
        m.put("caseType", of(Enums.CaseType.values()));
        m.put("judgmentFor", of(Enums.JudgmentFor.values()));
        m.put("appealStatus", of(Enums.AppealStatus.values()));
        m.put("appealBy", of(Enums.AppealBy.values()));
        m.put("hearingType", of(Enums.HearingType.values()));
        m.put("execStatus", of(Enums.ExecStatus.values()));
        m.put("orderType", of(Enums.OrderType.values()));
        m.put("orderStatus", of(Enums.OrderStatus.values()));
        m.put("consultStatus", of(Enums.ConsultStatus.values()));
        m.put("priority", of(Enums.Priority.values()));
        m.put("archiveType", of(Enums.ArchiveType.values()));
        m.put("partyType", of(Enums.PartyType.values()));
        m.put("notificationType", of(Enums.NotificationType.values()));

        m.put("roles", roleRepository.findAll().stream()
                .map(r -> new Option(r.getCode(), r.getNameAr())).toList());

        List<User> active = userRepository.findByActiveTrue();
        m.put("lawyers", people(active, Permission.CASE_MANAGE));
        m.put("consultants", people(active, Permission.CONSULT_MANAGE));
        m.put("reviewers", people(active, Permission.CONSULT_REVIEW));

        m.put("clients", parties(Party.Kind.CLIENT));
        m.put("debtors", parties(Party.Kind.DEBTOR));

        m.put("specializations", active.stream()
                .map(User::getSpecialization)
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .distinct()
                .sorted()
                .toList());

        m.put("canManageUsers", securityUtils.has(Permission.USERS_MANAGE));
        return m;
    }

    private static List<Option> of(Enums.Labeled[] values) {
        List<Option> list = new ArrayList<>(values.length);
        for (Enums.Labeled v : values) {
            list.add(new Option(((Enum<?>) v).name(), v.label()));
        }
        return list;
    }

    private static List<PersonOption> people(List<User> users, String permission) {
        return users.stream()
                .filter(u -> u.has(permission))
                .map(u -> new PersonOption(u.getId(), u.getFullName(), u.getSpecialization()))
                .sorted(Comparator.comparing(PersonOption::name, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private List<PersonOption> parties(Party.Kind kind) {
        return partyRepository.findByKindAndActiveTrue(kind).stream()
                .map(p -> new PersonOption(p.getId(), p.getName(), p.getPhone()))
                .sorted(Comparator.comparing(PersonOption::name, Comparator.nullsLast(String::compareTo)))
                .toList();
    }
}
