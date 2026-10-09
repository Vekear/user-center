package com.example.usercenter.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.usercenter.common.BizException;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.entity.Role;
import com.example.usercenter.entity.UserRole;
import com.example.usercenter.mapper.RoleMapper;
import com.example.usercenter.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色读写。对外一律用短码（USER/ADMIN），库里存 ROLE_ 前缀，转换只在这里发生。
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    private static final Set<String> ALLOWED_SHORT_CODES = Set.of("USER", "ADMIN");

    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;

    public List<String> roleCodesOf(Long userId) {
        return roleMapper.selectRoleCodesByUserId(userId);
    }

    /**
     * 批量取角色，避免管理后台列表出现 N+1 查询。
     */
    public Map<Long, List<String>> roleCodesOfUsers(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<UserRole> relations = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().in(UserRole::getUserId, userIds));
        if (relations.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> codeById = roleMapper.selectList(null).stream()
                .collect(Collectors.toMap(Role::getId, Role::getCode));
        Map<Long, List<String>> result = new LinkedHashMap<>();
        for (UserRole relation : relations) {
            String code = codeById.get(relation.getRoleId());
            if (code != null) {
                result.computeIfAbsent(relation.getUserId(), key -> new java.util.ArrayList<>()).add(code);
            }
        }
        return result;
    }

    public List<Role> findAll() {
        return roleMapper.selectList(new LambdaQueryWrapper<Role>().orderByAsc(Role::getId));
    }

    public void assignDefault(Long userId) {
        assign(userId, List.of("USER"));
    }

    /**
     * 整体替换某用户的角色（幂等）。
     */
    public void assign(Long userId, List<String> shortCodes) {
        List<String> normalized = normalizeAll(shortCodes);
        Map<String, Long> idByCode = roleMapper.selectList(null).stream()
                .collect(Collectors.toMap(Role::getCode, Role::getId));
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        for (String code : normalized) {
            Long roleId = idByCode.get(code);
            if (roleId == null) {
                throw BizException.of(ErrorCode.SERVER_ERROR, "角色未初始化：" + code);
            }
            userRoleMapper.insert(new UserRole(userId, roleId));
        }
    }

    public List<String> normalizeAll(List<String> shortCodes) {
        if (shortCodes == null || shortCodes.isEmpty()) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "至少需要一个角色");
        }
        return shortCodes.stream()
                .map(this::normalize)
                .distinct()
                .sorted()
                .toList();
    }

    public String normalize(String shortCode) {
        if (shortCode == null) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "角色不能为空");
        }
        String shortForm = shortCode.toUpperCase(Locale.ROOT).replace("ROLE_", "");
        if (!ALLOWED_SHORT_CODES.contains(shortForm)) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "角色取值不合法：" + shortCode);
        }
        return "ROLE_" + shortForm;
    }

    public boolean hasAdminRole(List<String> roleCodes) {
        return roleCodes != null && roleCodes.contains(Role.ROLE_ADMIN);
    }

    /** 仍然可用的某角色用户数（排除已删除与已禁用），用于"最后一个管理员"守卫。 */
    public long countActiveUsersWithRole(String shortCode) {
        return roleMapper.countActiveUsersWithRole(normalize(shortCode));
    }
}
