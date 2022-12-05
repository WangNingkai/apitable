package com.vikadata.api.shared.cache.service.impl;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

import com.vikadata.api.shared.cache.bean.AccountLinkDto;
import com.vikadata.api.shared.cache.bean.UserLinkInfo;
import com.vikadata.api.shared.cache.service.UserLinkInfoService;
import com.vikadata.api.enterprise.vcode.enums.VCodeType;
import com.vikadata.api.user.mapper.DeveloperMapper;
import com.vikadata.api.player.mapper.PlayerActivityMapper;
import com.vikadata.api.user.mapper.UserLinkMapper;
import com.vikadata.api.enterprise.vcode.mapper.VCodeMapper;
import com.vikadata.core.constants.RedisConstants;
import com.vikadata.entity.DeveloperEntity;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UserLinkInfoInRedisServiceImpl implements UserLinkInfoService {

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Resource
    private UserLinkMapper userLinkMapper;

    @Resource
    private DeveloperMapper developerMapper;

    @Resource
    private PlayerActivityMapper playerActivityMapper;

    @Resource
    private VCodeMapper vCodeMapper;

    private static final int TIMEOUT = 30;

    @Override
    public UserLinkInfo getUserLinkInfo(Long userId) {
        String str = redisTemplate.opsForValue().get(RedisConstants.getUserLinkInfoKey(userId));
        if (str != null) {
            return JSONUtil.toBean(str, UserLinkInfo.class);
        }
        UserLinkInfo info = new UserLinkInfo();
        DeveloperEntity developerEntity = developerMapper.selectByUserId(userId);
        if (developerEntity != null) {
            info.setApiKey(developerEntity.getApiKey());
        }
        List<AccountLinkDto> accountLinkList = userLinkMapper.selectVoByUserId(userId);
        info.setAccountLinkList(accountLinkList);
        String actions = playerActivityMapper.selectActionsByUserId(userId);
        info.setWizards(actions);
        String inviteCode = vCodeMapper.selectCodeByTypeAndRefId(VCodeType.PERSONAL_INVITATION_CODE.getType(), userId);
        info.setInviteCode(inviteCode);
        redisTemplate.opsForValue().set(RedisConstants.getUserLinkInfoKey(userId), JSONUtil.toJsonStr(info), TIMEOUT, TimeUnit.MINUTES);
        return info;
    }

    @Override
    public void delete(Long userId) {
        redisTemplate.delete(RedisConstants.getUserLinkInfoKey(userId));
    }
}
