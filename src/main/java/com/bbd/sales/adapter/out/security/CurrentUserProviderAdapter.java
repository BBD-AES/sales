package com.bbd.sales.adapter.out.security;

import com.bbd.sales.application.port.out.CurrentUserProvider;
import com.bbd.sales.global.security.CurrentUser;
import com.bbd.sales.global.security.RoleType;
import com.bbd.securitycore.application.model.CurrentUserSnapshotResult;
import com.bbd.securitycore.application.port.in.GetCurrentUserSnapshotUseCase;
import com.bbd.securitycore.domain.TenancyType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrentUserProviderAdapter implements CurrentUserProvider {

    private final GetCurrentUserSnapshotUseCase getCurrentUserSnapshot;

    @Override
    public CurrentUser current() {
        CurrentUserSnapshotResult s = getCurrentUserSnapshot.getCurrentUserSnapshot();
        RoleType role = RoleType.valueOf(s.role().name()); // UserRole <-> RoleType 동일 상수
        String warehouseName = s.tenancyType() == TenancyType.BRANCH ? s.tenancyName() : null;
        return new CurrentUser(s.employeeNumber(), role, warehouseName);
    }
}
