package com.shelf.admin.config;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class StpInterfaceImpl implements StpInterface {

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        // 从 Sa-Token Session 里读取登录时写入的角色列表
        SaSession session = StpUtil.getSessionByLoginId(loginId);
        if (session == null) {
            return Collections.emptyList();
        }
        Object roleList = session.get(SaSession.ROLE_LIST);
        if (roleList instanceof List) {
            return (List<String>) roleList;
        }
        return Collections.emptyList();
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 暂时不用权限码，返回空
        return Collections.emptyList();
    }
}