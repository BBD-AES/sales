package com.bbd.sales.global.security;

/**
 * 인증 주체. 지금은 임시로 X-Employee-Number / X-Role / X-Warehouse-Code 헤더에서 만든다.
 * JWT 도입 시 이 객체를 채우는 곳(CurrentUserArgumentResolver)만 교체하면 된다.
 *
 * '권한 판단' 헬퍼를 여기 두어 서비스의 if 문이 의미로 읽히게 한다.
 */
public record CurrentUser(
        String employeeNumber,
        RoleType role,
        String warehouseCode
) {
    public boolean isAdmin() {
        return role == RoleType.ADMIN;
    }

    /** 본사 사용자(목록/상세 전체 조회 가능). */
    public boolean isHq() {
        return role == RoleType.ADMIN || role == RoleType.HQ_MANAGER || role == RoleType.HQ_STAFF;
    }

    /** 승인/반려 결정 권한(본사 관리자 또는 관리자). */
    public boolean canDecide() {
        return role == RoleType.ADMIN || role == RoleType.HQ_MANAGER;
    }

    /** 지점 사용자. */
    public boolean isBranchUser() {
        return role == RoleType.BRANCH_MANAGER || role == RoleType.BRANCH_STAFF;
    }

    /** 지점 관리자(HQ 제출 결정 권한). */
    public boolean isBranchManager() {
        return role == RoleType.BRANCH_MANAGER;
    }
}
