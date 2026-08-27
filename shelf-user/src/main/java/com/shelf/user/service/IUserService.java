package com.shelf.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.shelf.user.domain.dto.UserLoginDTO;
import com.shelf.user.domain.dto.UserProfileDTO;
import com.shelf.user.domain.dto.UserRegisterDTO;
import com.shelf.user.domain.vo.UserInfoVO;
import com.shelf.user.domain.vo.UserLoginVO;
import com.shelf.user.entity.User;


public interface IUserService extends IService<User> {

    UserLoginVO login(UserLoginDTO dto);

    void register(UserRegisterDTO dto);

    UserInfoVO getUserInfo(Long userId);

    /**
     * 完善/更新用户资料（注册后第二步）
     */
    void updateProfile(Long userId, UserProfileDTO dto);
}