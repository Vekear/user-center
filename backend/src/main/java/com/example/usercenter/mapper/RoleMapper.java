package com.example.usercenter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.usercenter.entity.Role;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

public interface RoleMapper extends BaseMapper<Role> {

    @Select("SELECT r.code FROM role r JOIN user_role ur ON ur.role_id = r.id WHERE ur.user_id = #{userId} ORDER BY r.id")
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    @Select("""
            <script>
            SELECT r.code FROM role r JOIN user_role ur ON ur.role_id = r.id WHERE ur.user_id IN
            <foreach collection="userIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<String> selectRoleCodesByUserIds(@Param("userIds") Collection<Long> userIds);

    /**
     * 统计"仍然可用"的某角色用户数：逻辑删除的不算，被禁用的也不算——
     * 用来守住"系统至少保留一个可用管理员"。
     */
    @Select("""
            SELECT COUNT(*) FROM `user` u
            JOIN user_role ur ON ur.user_id = u.id
            JOIN role r ON r.id = ur.role_id
            WHERE r.code = #{roleCode} AND u.deleted = 0 AND u.status = 'ACTIVE'
            """)
    long countActiveUsersWithRole(@Param("roleCode") String roleCode);
}
