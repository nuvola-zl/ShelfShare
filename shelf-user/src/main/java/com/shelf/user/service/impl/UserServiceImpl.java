package com.shelf.user.service.impl;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shelf.common.code.ErrorCode;
import com.shelf.common.exception.BusinessException;
import com.shelf.user.domain.dto.UserLoginDTO;
import com.shelf.user.domain.dto.UserProfileDTO;
import com.shelf.user.domain.dto.UserRegisterDTO;
import com.shelf.user.domain.vo.UserInfoVO;
import com.shelf.user.domain.vo.UserLoginVO;
import com.shelf.user.entity.User;
import com.shelf.user.entity.UserBorrowQuota;
import com.shelf.user.mapper.UserMapper;
import com.shelf.user.service.IUserBorrowQuotaService;
import com.shelf.user.service.IUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private final IUserBorrowQuotaService quotaService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public UserLoginVO login(UserLoginDTO dto) {
        // 1. 查用户
        User user = getOne(new LambdaQueryWrapper<User>()
                .eq(User::getStudentNo, dto.getStudentNo()));
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "学号或密码错误");
        }

        // 2. 校验密码
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "学号或密码错误");
        }

        // 3. Sa-Token 登录
        StpUtil.login(user.getId());

        // 写入角色：
        //  1) Sa-Token 标准角色列表（供 @SaCheckRole("ADMIN") 校验）
        //  2) 自定义 session key（供网关 UserContextTransmitFilter 透传 X-User-Role）
        String role = (user.getRole() != null && user.getRole() == 1) ? "ADMIN" : "USER";
        StpUtil.getSession().set(SaSession.ROLE_LIST, List.of(role));
        StpUtil.getSession().set("role", role);

        // 4. 组装返回
        UserLoginVO vo = new UserLoginVO();
        BeanUtil.copyProperties(user, vo);
        vo.setToken(StpUtil.getTokenValue());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(UserRegisterDTO dto) {
        // 1. 校验学号唯一
        Long count = baseMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getStudentNo, dto.getStudentNo()));
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "学号已被注册");
        }

        // 2. 校验手机号唯一
        count = baseMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getPhone, dto.getPhone()));
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "手机号已被注册");
        }

        // 3. 加密密码 & 组装用户（只填核心字段，可选字段走数据库默认值）
        User user = new User();
        BeanUtil.copyProperties(dto, user);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRole(0); // 默认学生

        // 4. 插入用户
        baseMapper.insert(user);

        // 5. 同步初始化借阅额度
        UserBorrowQuota quota = new UserBorrowQuota();
        quota.setUserId(user.getId());
        quota.setCurrentBorrowCount(0);
        quota.setTotalBorrowCount(0);
        quota.setOverdueCount(0);
        quotaService.save(quota);

        log.info("用户注册成功: studentNo={}, userId={}", dto.getStudentNo(), user.getId());
    }

    @Override
    public UserInfoVO getUserInfo(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户不存在");
        }
        UserInfoVO vo = new UserInfoVO();
        BeanUtil.copyProperties(user, vo);
        return vo;
    }

    @Override
    public void updateProfile(Long userId, UserProfileDTO dto) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户不存在");
        }

        // 只更新非空字段（允许清空的话去掉判断）
        if (dto.getNickname() != null) {
            user.setNickname(dto.getNickname());
        }
        if (dto.getAvatar() != null) {
            user.setAvatar(dto.getAvatar());
        }
        if (dto.getCollege() != null) {
            user.setCollege(dto.getCollege());
        }
        if (dto.getMajor() != null) {
            user.setMajor(dto.getMajor());
        }
        if (dto.getGrade() != null) {
            user.setGrade(dto.getGrade());
        }
        if (dto.getGender() != null) {
            user.setGender(dto.getGender());
        }

        baseMapper.updateById(user);
        log.info("用户资料更新成功: userId={}", userId);
    }
}