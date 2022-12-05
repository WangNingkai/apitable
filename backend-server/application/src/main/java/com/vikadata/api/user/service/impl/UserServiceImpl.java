package com.vikadata.api.user.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.PageUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.toolkit.SqlHelper;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;

import com.vikadata.api.asset.service.IAssetService;
import com.vikadata.api.base.enums.DatabaseException;
import com.vikadata.api.base.service.IAuthService;
import com.vikadata.api.user.mapper.DeveloperMapper;
import com.vikadata.api.enterprise.integral.service.IIntegralService;
import com.vikadata.api.enterprise.vcode.dto.VCodeDTO;
import com.vikadata.api.enterprise.vcode.enums.VCodeException;
import com.vikadata.api.enterprise.vcode.enums.VCodeType;
import com.vikadata.api.enterprise.vcode.mapper.VCodeMapper;
import com.vikadata.api.enterprise.vcode.service.IVCodeService;
import com.vikadata.api.enterprise.vcode.service.IVCodeUsageService;
import com.vikadata.api.interfaces.social.enums.SocialNameModified;
import com.vikadata.api.interfaces.social.facade.SocialServiceFacade;
import com.vikadata.api.interfaces.social.model.SocialUserBind;
import com.vikadata.api.organization.dto.MemberDTO;
import com.vikadata.api.organization.mapper.MemberMapper;
import com.vikadata.api.organization.service.IMemberService;
import com.vikadata.api.player.ro.NotificationCreateRo;
import com.vikadata.api.player.service.IPlayerActivityService;
import com.vikadata.api.player.service.IPlayerNotificationService;
import com.vikadata.api.shared.cache.bean.LoginUserDto;
import com.vikadata.api.shared.cache.bean.OpenedSheet;
import com.vikadata.api.shared.cache.bean.UserLinkInfo;
import com.vikadata.api.shared.cache.bean.UserSpaceDto;
import com.vikadata.api.shared.cache.service.LoginUserService;
import com.vikadata.api.shared.cache.service.UserActiveSpaceService;
import com.vikadata.api.shared.cache.service.UserLinkInfoService;
import com.vikadata.api.shared.cache.service.UserSpaceOpenedSheetService;
import com.vikadata.api.shared.cache.service.UserSpaceService;
import com.vikadata.api.shared.component.LanguageManager;
import com.vikadata.api.shared.component.TaskManager;
import com.vikadata.api.shared.component.notification.INotificationFactory;
import com.vikadata.api.shared.component.notification.NotificationManager;
import com.vikadata.api.shared.component.notification.NotificationTemplateId;
import com.vikadata.api.shared.config.properties.ConstProperties;
import com.vikadata.api.shared.config.security.Auth0UserProfile;
import com.vikadata.api.shared.constants.IntegralActionCodeConstants;
import com.vikadata.api.shared.constants.LanguageConstants;
import com.vikadata.api.shared.constants.NotificationConstants;
import com.vikadata.api.shared.context.LoginContext;
import com.vikadata.api.shared.context.SessionContext;
import com.vikadata.api.shared.sysconfig.notification.NotificationTemplate;
import com.vikadata.api.shared.util.ApiHelper;
import com.vikadata.api.shared.util.RandomExtendUtil;
import com.vikadata.api.space.mapper.SpaceMapper;
import com.vikadata.api.space.ro.SpaceUpdateOpRo;
import com.vikadata.api.space.service.ISpaceInviteLinkService;
import com.vikadata.api.space.service.ISpaceService;
import com.vikadata.api.user.entity.UserEntity;
import com.vikadata.api.user.entity.UserHistoryEntity;
import com.vikadata.api.user.enums.UserClosingException;
import com.vikadata.api.user.enums.UserOperationType;
import com.vikadata.api.user.mapper.UserLinkMapper;
import com.vikadata.api.user.mapper.UserMapper;
import com.vikadata.api.user.model.UserInPausedDto;
import com.vikadata.api.user.model.UserLangDTO;
import com.vikadata.api.user.ro.DtBindOpRo;
import com.vikadata.api.user.ro.UserOpRo;
import com.vikadata.api.enterprise.user.service.IUserBindService;
import com.vikadata.api.enterprise.user.service.IUserHistoryService;
import com.vikadata.api.user.service.IUserService;
import com.vikadata.api.user.vo.UserInfoVo;
import com.vikadata.api.user.vo.UserLinkVo;
import com.vikadata.api.workspace.service.INodeShareService;
import com.vikadata.core.constants.RedisConstants;
import com.vikadata.core.exception.BusinessException;
import com.vikadata.core.util.ExceptionUtil;
import com.vikadata.core.util.HttpContextUtil;
import com.vikadata.core.util.SpringContextHolder;
import com.vikadata.core.util.SqlTool;
import com.vikadata.entity.DeveloperEntity;
import com.vikadata.api.organization.entity.MemberEntity;
import com.vikadata.entity.SpaceEntity;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.vikadata.api.organization.enums.OrganizationException.INVITE_EMAIL_HAS_LINK;
import static com.vikadata.api.organization.enums.OrganizationException.INVITE_EMAIL_NOT_EXIT;
import static com.vikadata.api.shared.constants.AssetsPublicConstants.PUBLIC_PREFIX;
import static com.vikadata.api.shared.constants.NotificationConstants.EXTRA_TOAST;
import static com.vikadata.api.shared.constants.NotificationConstants.EXTRA_TOAST_URL;
import static com.vikadata.api.shared.constants.SpaceConstants.SPACE_NAME_DEFAULT_SUFFIX;
import static com.vikadata.api.user.enums.UserException.LINK_EMAIL_ERROR;
import static com.vikadata.api.user.enums.UserException.LINK_FAILURE;
import static com.vikadata.api.user.enums.UserException.MOBILE_NO_EXIST;
import static com.vikadata.api.user.enums.UserException.MODIFY_PASSWORD_ERROR;
import static com.vikadata.api.user.enums.UserException.MUST_BIND_EAMIL;
import static com.vikadata.api.user.enums.UserException.MUST_BIND_MOBILE;
import static com.vikadata.api.user.enums.UserException.REGISTER_EMAIL_ERROR;
import static com.vikadata.api.user.enums.UserException.REGISTER_EMAIL_HAS_EXIST;
import static com.vikadata.api.user.enums.UserException.REGISTER_FAIL;
import static com.vikadata.api.user.enums.UserException.SIGN_IN_ERROR;
import static com.vikadata.api.user.enums.UserException.USERNAME_OR_PASSWORD_ERROR;
import static com.vikadata.api.user.enums.UserException.USER_LANGUAGE_SET_UN_SUPPORTED;
import static com.vikadata.api.user.enums.UserException.USER_NOT_EXIST;
import static com.vikadata.api.user.enums.UserOperationType.COMPLETE_CLOSING;

/**
 * <p>
 * User table service implementation class
 * </p>
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, UserEntity> implements IUserService {

    @Resource
    private LoginUserService loginUserService;

    @Resource
    private UserLinkMapper userLinkMapper;

    @Resource
    private IAssetService iAssetService;

    @Resource
    private ISpaceService iSpaceService;

    @Resource
    private IPlayerActivityService iPlayerActivityService;

    @Resource
    private IVCodeService ivCodeService;

    @Resource
    private UserLinkInfoService userLinkInfoService;

    @Resource
    private UserActiveSpaceService userActiveSpaceService;

    @Resource
    private UserSpaceService userSpaceService;

    @Resource
    private UserSpaceOpenedSheetService userSpaceOpenedSheetService;

    @Resource
    private INodeShareService nodeShareService;

    @Resource
    private ISpaceInviteLinkService spaceInviteLinkService;

    @Resource
    private IPlayerNotificationService notificationService;

    @Resource
    private MemberMapper memberMapper;

    @Resource
    private SpaceMapper spaceMapper;

    @Resource
    private ConstProperties constProperties;

    @Resource
    private FindByIndexNameSessionRepository<? extends Session> sessions;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private IIntegralService iIntegralService;

    @Resource
    private DeveloperMapper developerMapper;

    @Resource
    private IVCodeUsageService ivCodeUsageService;

    @Resource
    private IAuthService iAuthService;

    @Resource
    private IUserHistoryService iUserHistoryService;

    @Resource
    private UserMapper userMapper;

    @Resource
    private SocialServiceFacade socialServiceFacade;

    @Resource
    private IMemberService iMemberService;

    @Resource
    private VCodeMapper vCodeMapper;

    @Resource
    private IUserBindService iUserBindService;

    @Resource
    private INotificationFactory notificationFactory;

    private static final int QUERY_LOCALE_IN_EMAILS_LIMIT = 200;

    @Override
    public Long getUserIdByMobile(String mobile) {
        return baseMapper.selectIdByMobile(mobile);
    }

    @Override
    public Long getUserIdByEmail(String email) {
        return baseMapper.selectIdByEmail(email);
    }

    @Override
    public boolean checkByCodeAndMobile(String code, String mobile) {
        code = StrUtil.prependIfMissing(code, "+");
        UserEntity userEntity = baseMapper.selectByMobile(mobile);
        if (userEntity == null) {
            return false;
        }
        return StrUtil.isNotBlank(userEntity.getCode()) && userEntity.getCode().equals(code);
    }

    @Override
    public boolean checkByEmail(String email) {
        return SqlTool.retCount(baseMapper.selectCountByEmail(email)) > 0;
    }

    @Override
    public UserEntity getByCodeAndMobilePhone(String code, String mobilePhone) {
        code = StrUtil.prependIfMissing(code, "+");
        UserEntity userEntity = baseMapper.selectByMobile(mobilePhone);
        if (userEntity == null) {
            return null;
        }
        if (StrUtil.isNotBlank(userEntity.getCode()) && userEntity.getCode().equals(code)) {
            return userEntity;
        }
        return null;
    }

    @Override
    public List<UserEntity> getByCodeAndMobilePhones(String code, Collection<String> mobilePhones) {
        List<UserEntity> userEntities = baseMapper.selectByMobilePhoneIn(mobilePhones);
        if (userEntities.isEmpty()) {
            return userEntities;
        }
        String finalCode = StrUtil.prependIfMissing(code, "+");
        return userEntities.stream().filter(user -> StrUtil.isNotBlank(user.getCode()) && user.getCode().equals(finalCode))
                .collect(Collectors.toList());
    }

    @Override
    public UserEntity getByEmail(String email) {
        return baseMapper.selectByEmail(email);
    }

    @Override
    public List<UserEntity> getByEmails(Collection<String> emails) {
        return baseMapper.selectByEmails(emails);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createByExternalSystem(String externalId, String nickName, String avatar, String email, String remark) {
        if (StrUtil.isNotBlank(email)) {
            // Query whether the existing mail exists
            UserEntity selectUser = baseMapper.selectByEmail(email);
            if (selectUser != null) {
                UserEntity user = new UserEntity();
                user.setId(selectUser.getId());
                user.setNickName(nickName);
                user.setAvatar(avatar);
                user.setLastLoginTime(LocalDateTime.now());
                user.setRemark(remark);
                boolean flag = updateById(user);
                if (!flag) {
                    throw new BusinessException(SIGN_IN_ERROR);
                }
                socialServiceFacade.createSocialUser(new SocialUserBind(selectUser.getId(), externalId));
                return selectUser.getId();
            }
        }
        log.info("Create Account");
        UserEntity user = new UserEntity();
        user.setUuid(IdUtil.fastSimpleUUID());
        user.setNickName(StrUtil.isNotBlank(nickName) ? nickName : StrUtil.format("星球居民{}", RandomExtendUtil.randomString(4)));
        user.setAvatar(StrUtil.isNotBlank(avatar) ? avatar : getRandomAvatar());
        user.setEmail(email);
        user.setLastLoginTime(LocalDateTime.now());
        user.setRemark(remark);
        boolean flag = saveUser(user);
        if (!flag) {
            throw new BusinessException(SIGN_IN_ERROR);
        }
        if (StrUtil.isNotBlank(email)) {
            // If the mail has been invited and has not been bound to other accounts, activate the space members of the invited mail
            List<MemberDTO> inactiveMembers = iMemberService.getInactiveMemberByEmails(email);
            inactiveMemberProcess(user.getId(), inactiveMembers);
        }
        else {
            String spaceName = user.getNickName();
            if (LocaleContextHolder.getLocale().equals(LanguageManager.me().getDefaultLanguage())) {
                spaceName += SPACE_NAME_DEFAULT_SUFFIX;
            }
            iSpaceService.createSpace(user, spaceName);
        }
        // Create user activity record
        iPlayerActivityService.createUserActivityRecord(user.getId());
        // Create personal invitation code
        ivCodeService.createPersonalInviteCode(user.getId());
        // Create Associated User
        socialServiceFacade.createSocialUser(new SocialUserBind(user.getId(), externalId));
        return user.getId();
    }

    @Override
    public boolean saveUser(UserEntity user) {
        boolean flag = save(user);
        TaskManager.me().execute(() -> {
            // jump to third site
            NotificationTemplate template =
                    notificationFactory.getTemplateById(NotificationTemplateId.NEW_USER_WELCOME_NOTIFY.getValue());
            Dict extras = Dict.create();
            if (StrUtil.isNotBlank(template.getUrl()) && template.getUrl().startsWith("http")) {
                Dict toast = Dict.create();
                toast.put(EXTRA_TOAST_URL, template.getUrl());
                toast.put("onClose", ListUtil.toList("mark_cur_notice_to_read()"));
                toast.put("onBtnClick", ListUtil.toList("window_open_url()"));
                toast.put("duration", 0);
                toast.put("closable", true);
                extras.put(EXTRA_TOAST, toast);
            }
            NotificationManager.me().playerNotify(NotificationTemplateId.NEW_USER_WELCOME_NOTIFY,
                    Collections.singletonList(user.getId()), 0L, null, extras);
        });
        return flag;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createUserByAuth0IfNotExist(Auth0UserProfile userProfile) {
        Long userId = iUserBindService.getUserIdByExternalKey(userProfile.getSub());
        if (userId == null) {
            // create user bind
            UserEntity userEntity = buildUserEntity(userProfile.getPicture(), userProfile.getNickname(), userProfile.getEmail());
            saveUser(userEntity);
            // Create user activity record
            iPlayerActivityService.createUserActivityRecord(userEntity.getId());
            // Create personal invitation code
            ivCodeService.createPersonalInviteCode(userEntity.getId());
            // create user bind
            iUserBindService.create(userEntity.getId(), userProfile.getSub());
            // init one space for user
            initialDefaultSpaceForUser(userEntity);
            userId = userEntity.getId();
        }
        List<MemberDTO> inactiveMembers = iMemberService.getInactiveMemberDtoByEmail(userProfile.getEmail());
        List<Long> memberIds = inactiveMembers.stream().map(MemberDTO::getId).collect(Collectors.toList());
        activeInvitationSpace(userId, memberIds);
        return userId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createUserByAuth0IfNotExist(com.auth0.json.mgmt.users.User user) {
        Long userId = iUserBindService.getUserIdByExternalKey(user.getId());
        if (userId == null) {
            // create user bind
            UserEntity userEntity = buildUserEntity(user.getPicture(), user.getNickname(), user.getEmail());
            saveUser(userEntity);
            // Create user activity record
            iPlayerActivityService.createUserActivityRecord(userEntity.getId());
            // Create personal invitation code
            ivCodeService.createPersonalInviteCode(userEntity.getId());
            // create user bind
            iUserBindService.create(userEntity.getId(), user.getId());
            // init one space for user
            initialDefaultSpaceForUser(userEntity);
            userId = userEntity.getId();
        }
        List<MemberDTO> inactiveMembers = iMemberService.getInactiveMemberDtoByEmail(user.getEmail());
        List<Long> memberIds = inactiveMembers.stream().map(MemberDTO::getId).collect(Collectors.toList());
        activeInvitationSpace(userId, memberIds);
        return userId;
    }

    private UserEntity buildUserEntity(String picture, String nickname, String email) {
        String avatar = iAssetService.downloadAndUploadUrl(picture);
        return UserEntity.builder()
                .uuid(IdUtil.fastSimpleUUID())
                .nickName(nickname)
                .avatar(avatar)
                .email(email)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(String areaCode, String mobile, String nickName, String avatar, String email, String spaceName) {
        // Create user with mobile number
        UserEntity entity = UserEntity.builder()
                .uuid(IdUtil.fastSimpleUUID())
                .code(areaCode)
                .mobilePhone(mobile)
                .nickName(nullToDefaultNickName(nickName))
                .avatar(nullToDefaultAvatar(avatar))
                .email(email)
                .lastLoginTime(LocalDateTime.now())
                .build();
        boolean flag = saveUser(entity);
        ExceptionUtil.isTrue(flag, REGISTER_FAIL);
        boolean hasSpace = false;
        if (email != null) {
            // If the mailbox has been invited and has not been bound to other accounts, activate the space members of the invited mailbox
            List<MemberDTO> inactiveMembers = iMemberService.getInactiveMemberByEmails(email);
            hasSpace = this.inactiveMemberProcess(entity.getId(), inactiveMembers);
        }
        // Activate imported members
        if (mobile != null) {
            int count = memberMapper.updateUserIdByMobile(entity.getId(), mobile);
            hasSpace = hasSpace || count > 0;
        }
        // No space to create a space automatically
        if (!hasSpace) {
            String newSpaceName;
            if (StrUtil.isNotBlank(spaceName)) {
                newSpaceName = spaceName;
            }
            else {
                newSpaceName = entity.getNickName();
                if (LocaleContextHolder.getLocale().equals(LanguageManager.me().getDefaultLanguage())) {
                    newSpaceName += SPACE_NAME_DEFAULT_SUFFIX;
                }
            }
            iSpaceService.createSpace(entity, newSpaceName);
        }
        // Create user activity record
        iPlayerActivityService.createUserActivityRecord(entity.getId());
        // Create personal invitation code
        ivCodeService.createPersonalInviteCode(entity.getId());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserEntity createUserByMobilePhone(String areaCode, String mobile, String nickName, String avatar) {
        UserEntity entity = UserEntity.builder()
                .uuid(IdUtil.fastSimpleUUID())
                .code(areaCode)
                .mobilePhone(mobile)
                .nickName(nullToDefaultNickName(nickName))
                .avatar(nullToDefaultAvatar(avatar))
                .lastLoginTime(LocalDateTime.now())
                .build();
        boolean flag = saveUser(entity);
        ExceptionUtil.isTrue(flag, REGISTER_FAIL);
        // Create user activity record
        iPlayerActivityService.createUserActivityRecord(entity.getId());
        // Create personal invitation code
        ivCodeService.createPersonalInviteCode(entity.getId());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserEntity createUserByEmail(String email) {
        UserEntity entity = UserEntity.builder()
                .uuid(IdUtil.fastSimpleUUID())
                .email(email)
                .nickName(nullToDefaultNickName(null))
                .avatar(nullToDefaultAvatar(null))
                .lastLoginTime(LocalDateTime.now())
                .build();
        boolean flag = saveUser(entity);
        ExceptionUtil.isTrue(flag, REGISTER_FAIL);
        // Create user activity record
        iPlayerActivityService.createUserActivityRecord(entity.getId());
        // Create personal invitation code
        ivCodeService.createPersonalInviteCode(entity.getId());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createUsersByCli() {
        int size = 50;
        String u = "test";
        String p = "13312345";
        for (int i = 0; i < size; i++) {
            if (i < 10) {
                createUserByCli(u + String.format("00%d@vikatest.com", i), "qwer1234", p + String.format("00%d", i));
            }
            else {
                createUserByCli(u + String.format("0%d@vikatest.com", i), "qwer1234", p + String.format("0%d", i));
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserEntity createUserByCli(String username, String password, String phone) {
        log.info("Create User");
        ExceptionUtil.isTrue(Validator.isEmail(username), REGISTER_EMAIL_ERROR);
        UserEntity user = baseMapper.selectByEmail(username);
        ExceptionUtil.isNull(user, REGISTER_EMAIL_HAS_EXIST);
        UserEntity newUser = new UserEntity();
        newUser.setUuid(IdUtil.fastSimpleUUID());
        newUser.setEmail(username);
        newUser.setNickName(StrUtil.subBefore(username, '@', true));
        PasswordEncoder passwordEncoder = SpringContextHolder.getBean(PasswordEncoder.class);
        newUser.setPassword(passwordEncoder.encode(password));
        newUser.setCode("+86");
        newUser.setMobilePhone(phone);
        boolean saveFlag = saveUser(newUser);
        ExceptionUtil.isTrue(saveFlag, REGISTER_FAIL);
        String spaceName = newUser.getNickName();
        if (LocaleContextHolder.getLocale().equals(LanguageManager.me().getDefaultLanguage())) {
            spaceName += SPACE_NAME_DEFAULT_SUFFIX;
        }
        iSpaceService.createSpace(newUser, spaceName);
        // Create personal invitation code
        ivCodeService.createPersonalInviteCode(newUser.getId());
        // Create user activity record
        iPlayerActivityService.createUserActivityRecord(newUser.getId());
        DeveloperEntity developer = new DeveloperEntity();
        developer.setId(IdWorker.getId());
        developer.setUserId(newUser.getId());
        developer.setApiKey(ApiHelper.createKey());
        developer.setCreatedBy(0L);
        developer.setUpdatedBy(0L);
        developerMapper.insert(developer);
        return newUser;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initialDefaultSpaceForUser(UserEntity user) {
        // initial default space for new come user
        String spaceName = user.getNickName();
        if (LocaleContextHolder.getLocale().equals(LanguageManager.me().getDefaultLanguage())) {
            spaceName += SPACE_NAME_DEFAULT_SUFFIX;
        }
        iSpaceService.createSpace(user, spaceName);
    }

    @Override
    public void activeInvitationSpace(Long userId, List<Long> memberIds) {
        List<MemberEntity> memberEntities = new ArrayList<>();
        for (Long memberId : memberIds) {
            MemberEntity member = new MemberEntity();
            member.setId(memberId);
            member.setUserId(userId);
            member.setIsActive(true);
            memberEntities.add(member);
        }
        iMemberService.updateBatchById(memberEntities);
    }

    @Override
    public boolean checkUserHasBindEmail(Long userId) {
        log.info("Query whether users bind email");
        UserEntity user = getById(userId);
        ExceptionUtil.isNotNull(user, USER_NOT_EXIST);
        return user.getEmail() != null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindMemberByEmail(Long userId, String spaceId, String email) {
        log.info("Bind member email");
        // Determine whether the email is unbound and invited
        MemberEntity member = iMemberService.getBySpaceIdAndEmail(spaceId, email);
        ExceptionUtil.isNotNull(member, INVITE_EMAIL_NOT_EXIT);
        ExceptionUtil.isNull(member.getUserId(), INVITE_EMAIL_HAS_LINK);

        // Judge whether the requesting user's mailbox is bound to another email, and the user's email must be empty
        String userEmail = baseMapper.selectEmailById(userId);
        ExceptionUtil.isBlank(userEmail, LINK_EMAIL_ERROR);
        // Bind as user email, and the email will be activated by invited space members together
        updateEmailByUserId(userId, email);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateEmailByUserId(Long userId, String email) {
        log.info("Modify User [{}] email [{}]", userId, email);
        UserEntity updateUser = new UserEntity();
        updateUser.setId(userId);
        updateUser.setEmail(email);
        boolean flag = updateById(updateUser);
        ExceptionUtil.isTrue(flag, LINK_EMAIL_ERROR);
        // Synchronize member information email
        iMemberService.updateEmailByUserId(userId, email);
        // If the email has been invited and has not been bound to other accounts, activate the space members of the invited email
        List<MemberDTO> inactiveMembers = iMemberService.getInactiveMemberByEmails(email);
        this.inactiveMemberProcess(userId, inactiveMembers);
        // Delete Cache
        loginUserService.delete(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindEmailByUserId(Long userId) {
        // The user needs to bind at least one contact (mobile phone number, email) to unbind the email
        LoginUserDto userDto = loginUserService.getLoginUser(userId);
        ExceptionUtil.isNotBlank(userDto.getMobile(), MUST_BIND_MOBILE);
        boolean flag = SqlHelper.retBool(baseMapper.resetEmailByUserId(userId));
        ExceptionUtil.isTrue(flag, DatabaseException.EDIT_ERROR);
        // Synchronize unbound member information email
        iMemberService.resetEmailByUserId(userId);
        // Delete Cache
        loginUserService.delete(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMobileByUserId(Long userId, String code, String mobile) {
        LoginUserDto userDto = loginUserService.getLoginUser(userId);
        UserEntity updateUser = new UserEntity();
        updateUser.setId(userId);
        updateUser.setCode(code);
        updateUser.setMobilePhone(mobile);
        boolean flag = updateById(updateUser);
        ExceptionUtil.isTrue(flag, DatabaseException.EDIT_ERROR);
        // Synchronize the mobile number of member information
        iMemberService.updateMobileByUserId(userId, mobile);
        // If the mobile phone number has been invited and no other account has been bound, activate the invited space member
        List<MemberDTO> inactiveMembers = iMemberService.getInactiveMemberByEmails(mobile);
        this.inactiveMemberProcess(userId, inactiveMembers);

        // Delete Cache
        loginUserService.delete(userId);
        // Email registration is bound to mobile phones for the first time, and additional invitation rewards are given
        if (userDto.getMobile() == null) {
            TaskManager.me().execute(() -> {
                // Get the invitation code when registering
                VCodeDTO vCodeDTO = ivCodeUsageService.getInvitorUserId(userId);
                if (vCodeDTO == null) {
                    return;
                }
                // Judge the invitation code type
                boolean isPersonal = vCodeDTO.getType().equals(VCodeType.PERSONAL_INVITATION_CODE.getType());
                String actionCode = isPersonal ? IntegralActionCodeConstants.BE_INVITED_TO_REWARD
                        : IntegralActionCodeConstants.OFFICIAL_INVITATION_REWARD;
                // Each user can only enjoy one point reward
                int historyNum = iIntegralService.getCountByUserIdAndActionCode(userId, actionCode);
                if (historyNum >= 1) {
                    return;
                }
                // Personal invitation code reward
                if (isPersonal) {
                    iAuthService.personalInvitedReward(userId, userDto.getNickName(), vCodeDTO.getUserId());
                    return;
                }
                // Official invitation code award
                iAuthService.officialInvitedReward(userId);
            });
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindMobileByUserId(Long userId) {
        // The user needs to bind at least one contact (phone number, email) to unbind the mobile phone number
        LoginUserDto userDto = loginUserService.getLoginUser(userId);
        ExceptionUtil.isNotBlank(userDto.getEmail(), MUST_BIND_EAMIL);
        boolean flag = SqlHelper.retBool(baseMapper.resetMobileByUserId(userId));
        ExceptionUtil.isTrue(flag, DatabaseException.EDIT_ERROR);
        // Synchronize the mobile phone number of unbinding member information
        iMemberService.resetMobileByUserId(userId);
        // Delete Cache
        loginUserService.delete(userId);
    }

    @Override
    public void updateLoginTime(Long userId) {
        // Update the last login time
        UserEntity update = new UserEntity();
        update.setId(userId);
        update.setLastLoginTime(LocalDateTime.now());
        boolean flag = updateById(update);
        ExceptionUtil.isTrue(flag, SIGN_IN_ERROR);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void edit(Long userId, UserOpRo param) {
        log.info("Edit user information");
        UserEntity userEntity = getById(userId);
        ExceptionUtil.isNotNull(userEntity, USER_NOT_EXIST);
        UserEntity user = UserEntity.builder().id(userId).build();
        String waitDeleteOldAvatar = null;
        if (StrUtil.isNotBlank(param.getAvatar())) {
            user.setAvatar(param.getAvatar());
            waitDeleteOldAvatar = userEntity.getAvatar();
        }
        if (StrUtil.isNotBlank(param.getLocale())) {
            ExceptionUtil.isTrue(LanguageConstants.isLanguagesSupported(param.getLocale()), USER_LANGUAGE_SET_UN_SUPPORTED);
            user.setLocale(param.getLocale());
        }
        if (StrUtil.isNotBlank(param.getNickName())) {
            // Initialize the nickname. If there is a space "space of *** planet residents" registered and automatically created, synchronously modify the space name
            if (BooleanUtil.isTrue(param.getInit())) {
                String spaceId = spaceMapper.selectSpaceIdByUserIdAndName(userId, userEntity.getNickName());
                if (StrUtil.isNotBlank(spaceId)) {
                    String spaceName = param.getNickName();
                    if (LocaleContextHolder.getLocale().equals(LanguageManager.me().getDefaultLanguage())) {
                        spaceName += SPACE_NAME_DEFAULT_SUFFIX;
                    }
                    iSpaceService.updateSpace(userId, spaceId, SpaceUpdateOpRo.builder().name(spaceName).build());
                }
            }
            // Synchronize personal nickname to member name that has not been modified
            iMemberService.updateMemberNameByUserId(userId, param.getNickName());
            // Synchronously modify member 'Social Name Modified' field status
            memberMapper.updateSocialNameModifiedByUserId(userId);
            // Delete the space cache with modified member names
            TaskManager.me().execute(() -> {
                List<String> spaceIds = iMemberService.getSpaceIdWithoutNameModifiedByUserId(userId);
                for (String spcId : spaceIds) {
                    userSpaceService.delete(userId, spcId);
                }
            });
            user.setNickName(param.getNickName())
                    .setIsSocialNameModified(SocialNameModified.YES.getValue());
            if (BooleanUtil.isTrue(param.getInit())) {
                // If it is an invitation to reward, modify the user's name
                String key = RedisConstants.getInviteHistoryKey(userId.toString());
                if (BooleanUtil.isTrue(redisTemplate.hasKey(key))) {
                    Long recordId = Long.parseLong(StrUtil.toString(redisTemplate.opsForValue().get(key)));
                    iIntegralService.updateParameterById(recordId, JSONUtil.createObj().putOnce("userId", userId).putOnce("name", param.getNickName()).toString());
                    redisTemplate.delete(key);
                }
            }
        }
        boolean flag = updateById(user);
        ExceptionUtil.isTrue(flag, DatabaseException.EDIT_ERROR);
        // Delete Cache
        loginUserService.delete(userId);
        if (StrUtil.isNotBlank(waitDeleteOldAvatar) && StrUtil.startWith(waitDeleteOldAvatar, PUBLIC_PREFIX)) {
            // Delete original cloud files
            iAssetService.delete(waitDeleteOldAvatar);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePwd(Long id, String password) {
        log.info("Change Password");
        PasswordEncoder passwordEncoder = SpringContextHolder.getBean(PasswordEncoder.class);
        UserEntity user = UserEntity.builder()
                .id(id)
                .password(passwordEncoder.encode(password))
                .build();

        // Delete Cache
        loginUserService.delete(id);
        boolean flag = updateById(user);
        ExceptionUtil.isTrue(flag, MODIFY_PASSWORD_ERROR);
    }

    @Override
    public UserInfoVo getCurrentUserInfo(Long userId, String spaceId, Boolean filter) {
        log.info("Get user information and space content");
        // Query the user's basic information
        LoginUserDto loginUserDto = LoginContext.me().getLoginUser();
        UserLinkInfo userLinkInfo = userLinkInfoService.getUserLinkInfo(loginUserDto.getUserId());
        // Copy third-party account associated information
        List<UserLinkVo> thirdPartyInformation = new ArrayList<>(userLinkInfo.getAccountLinkList().size());
        for (int i = 0; i < userLinkInfo.getAccountLinkList().size(); i++) {
            UserLinkVo linkVo = new UserLinkVo();
            BeanUtil.copyProperties(userLinkInfo.getAccountLinkList().get(i), linkVo);
            thirdPartyInformation.add(linkVo);
        }
        // Whether the invitation code has been used for rewards
        boolean usedInviteReward = iIntegralService.checkByUserIdAndActionCodes(userId,
                CollectionUtil.newArrayList(IntegralActionCodeConstants.BE_INVITED_TO_REWARD, IntegralActionCodeConstants.OFFICIAL_INVITATION_REWARD));
        UserInfoVo userInfo = UserInfoVo.builder().sendSubscriptionNotify(constProperties.getSendSubscriptionNotify())
                .usedInviteReward(usedInviteReward)
                .build()
                .transferDataFromDto(loginUserDto, userLinkInfo, thirdPartyInformation);

        if (userInfo.getIsPaused()) { // Cancel the account during the calm period, and calculate the official cancellation time
            UserHistoryEntity userHistory = iUserHistoryService
                    .getLatestUserHistoryEntity(userId, UserOperationType.APPLY_FOR_CLOSING);
            ExceptionUtil.isNotNull(userHistory, UserClosingException.USER_HISTORY_RECORD_ISSUE);
            userInfo.setCloseAt(userHistory.getCreatedAt().plusDays(30).withHour(0).withMinute(0).withSecond(0));
        }

        boolean noSpace = StrUtil.isBlank(spaceId);
        // Selectively filter spatial related information
        if (BooleanUtil.isTrue(filter)) {
            if (noSpace) {
                return userInfo;
            }
            Long memberId = iMemberService.getMemberIdByUserIdAndSpaceId(userId, spaceId);
            if (ObjectUtil.isNull(memberId)) {
                return userInfo;
            }
        }
        else if (noSpace) {
            // When the space ID is not transferred, obtain the space ID of the user's recent work
            String activeSpaceId = userActiveSpaceService.getLastActiveSpace(userId);
            if (StrUtil.isBlank(activeSpaceId)) {
                return userInfo;
            }
            spaceId = activeSpaceId;
        }
        else {
            // Prevent access to not join spaces
            userSpaceService.getMemberId(userId, spaceId);
        }
        userInfo.setNeedCreate(false);
        // Cache session
        UserSpaceDto userSpace = userSpaceService.getUserSpace(userId, spaceId);
        userInfo.setSpaceId(userSpace.getSpaceId());
        userInfo.setSpaceName(userSpace.getSpaceName());
        userInfo.setSpaceLogo(userSpace.getSpaceLogo());
        userInfo.setMemberId(userSpace.getMemberId());
        userInfo.setMemberName(userSpace.getMemberName());
        userInfo.setUnitId(userSpace.getUnitId());
        userInfo.setIsAdmin(userSpace.isAdmin() || userSpace.isMainAdmin());
        userInfo.setIsMainAdmin(userSpace.isMainAdmin());
        userInfo.setIsDelSpace(userSpace.isDel());
        userInfo.setIsNewComer(!iMemberService.checkUserHasModifyNameInSpace(userId));
        userInfo.setIsMemberNameModified(userSpace.getIsMemberNameModified());

        // Get the last opened data table information
        OpenedSheet openedSheet = userSpaceOpenedSheetService.getOpenedSheet(userId, spaceId);
        if (ObjectUtil.isNotNull(openedSheet) && ObjectUtil.isNotNull(openedSheet.getNodeId())) {
            userInfo.setActiveNodeId(openedSheet.getNodeId());
            userInfo.setActiveViewId(openedSheet.getViewId());
            userInfo.setActiveNodePos(openedSheet.getPosition());
        }

        return userInfo;
    }

    @Override
    public void bindDingTalk(DtBindOpRo opRo) {
        log.info("Associated DingTalk");
        // Judge whether it exists
        Long id = baseMapper.selectIdByMobile(opRo.getPhone());
        ExceptionUtil.isNotNull(id, MOBILE_NO_EXIST);
        UserEntity user = UserEntity.builder()
                .id(id)
                .dingOpenId(opRo.getOpenId())
                .dingUnionId(opRo.getUnionId())
                .build();

        boolean flag = updateById(user);
        ExceptionUtil.isTrue(flag, LINK_FAILURE);
        // Bind successfully, and automatically log in to save the session
        SessionContext.setUserId(id);
    }

    @Override
    public void closeMultiSession(Long userId, boolean isRetain) {
        Collection<? extends Session> usersSessions = this.sessions.findByPrincipalName(userId.toString()).values();
        if (CollUtil.isNotEmpty(usersSessions)) {
            List<String> idList = usersSessions.stream().map(Session::getId).collect(Collectors.toList());
            if (isRetain) {
                HttpSession httpSession = HttpContextUtil.getSession(false);
                if (httpSession != null) {
                    idList.remove(httpSession.getId());
                }
            }
            for (String id : idList) {
                this.sessions.deleteById(id);
            }
        }
    }

    @Override
    public void unbind(Long userId, Integer type) {
        String linkUnionId = userLinkMapper.selectUnionIdByUserIdAndType(userId, type);
        // Delete third-party integration association
        socialServiceFacade.deleteByUnionId(Collections.singletonList(linkUnionId));
        // Delete account association
        userLinkMapper.deleteByUserIdAndType(userId, type);
    }

    @Override
    public String getUuidByUserId(Long userId) {
        return baseMapper.selectUuidById(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyForClosingAccount(UserEntity user) {
        // Update the user logoff cool down period status to Yes
        updateIsPaused(user.getId(), true);
        // Add User Operation Record
        iUserHistoryService.create(user, UserOperationType.APPLY_FOR_CLOSING);
        // Logically delete user share
        nodeShareService.disableNodeSharesByUserId(user.getId());
        // Delete user Login Dto cache
        loginUserService.delete(user.getId());
        userActiveSpaceService.delete(user.getId());
        // Logical deletion of space invite link
        List<MemberEntity> members = iMemberService.getByUserId(user.getId());
        if (members.size() == 0) {
            return;
        }
        List<Long> memberIds = members.stream().map(MemberEntity::getId).collect(Collectors.toList());
        spaceInviteLinkService.deleteByMemberIds(memberIds);
        // Logical delete member information
        iMemberService.preDelByMemberIds(memberIds);

        // notify the main admin about this member is going to close his account.
        List<String> spaceIds = members.stream().map(MemberEntity::getSpaceId).collect(Collectors.toList());
        List<SpaceEntity> spaces = iSpaceService.getBySpaceIds(spaceIds);
        if (spaces.size() == 0) {
            return;
        }
        List<NotificationCreateRo> notificationCreateRos = genNotificationCreateRos(user, spaces);
        notificationService.batchCreateNotify(notificationCreateRos);
    }

    /**
     * Encapsulate Notification to notify the master administrator that the member has applied for logoff
     *
     * @param user User
     * @param spaces Space List
     * @return NotificationCreateRo List
     */
    private List<NotificationCreateRo> genNotificationCreateRos(UserEntity user, List<SpaceEntity> spaces) {
        List<NotificationCreateRo> ros = Lists.newArrayList();
        spaces.forEach(spaceEntity -> {
            NotificationCreateRo notifyRo = new NotificationCreateRo();
            notifyRo.setSpaceId(spaceEntity.getSpaceId());
            String memberId = String.valueOf(spaceEntity.getOwner());
            notifyRo.setToMemberId(Lists.newArrayList(memberId));
            notifyRo.setFromUserId(String.valueOf(user.getId()));
            Dict extras = Dict.create().set("nickName", user.getNickName());
            JSONObject data = JSONUtil.createObj().putOnce(NotificationConstants.BODY_EXTRAS, extras)
                    .set("nickName", user.getNickName());
            notifyRo.setBody(data);
            notifyRo.setTemplateId(NotificationTemplateId.MEMBER_APPLIED_TO_CLOSE_ACCOUNT.getValue());
            ros.add(notifyRo);
        });
        return ros;
    }

    private void updateIsPaused(Long userId, boolean isPaused) {
        UserEntity userPaused = UserEntity.builder()
                .id(userId).isPaused(isPaused).build();
        baseMapper.updateById(userPaused);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelClosingAccount(UserEntity user) {
        // Update the user to log off the cool down period status to No
        updateIsPaused(user.getId(), false);
        // Get the member information that has not been logically deleted and the exceptions caused by compatible address book synchronization
        List<MemberEntity> unexpectedMembers = iMemberService.getByUserId(user.getId());
        List<Long> unexpectedMemberIds = unexpectedMembers.stream().map(MemberEntity::getId)
                .collect(Collectors.toList());
        // Logical deletion of abnormal member information
        if (unexpectedMemberIds.size() > 0) {
            memberMapper.deleteBatchByIds(unexpectedMemberIds);
        }
        // Restore member information
        iMemberService.cancelPreDelByUserId(user.getId());
        // Delete user Login Dto cache
        loginUserService.delete(user.getId());
        userActiveSpaceService.delete(user.getId());
        // Add User Operation Record
        UserHistoryEntity userHistory = UserHistoryEntity.builder().userId(user.getId())
                .userStatus(UserOperationType.CANCEL_CLOSING.getStatusCode())
                .uuid(user.getUuid())
                .build();
        iUserHistoryService.create(userHistory);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeAccount(UserEntity user) {
        // Clear the user's nickname, area code, mobile phone and email information
        userMapper.resetUserById(user.getId());
        // Clear the user's information in the member table
        memberMapper.clearMemberInfoByUserId(user.getId());
        // Physically delete the user's third-party association binding information
        userLinkMapper.deleteByUserId(user.getId());
        socialServiceFacade.deleteUser(user.getId());
        // Write the "Logout Completed" record to the history table. 0 represents the system user
        UserHistoryEntity userHistory = UserHistoryEntity.builder()
                .userId(user.getId())
                .uuid(user.getUuid())
                .userStatus(COMPLETE_CLOSING.getStatusCode())
                .createdBy(user.getId())
                .updatedBy(user.getId())
                .build();
        iUserHistoryService.create(userHistory);
    }

    @Override
    public List<UserInPausedDto> getPausedUserDtos(List<Long> userIds) {
        return userMapper.selectPausedUsers(userIds);
    }

    private boolean inactiveMemberProcess(Long userId, List<MemberDTO> inactiveMembers) {
        if (CollUtil.isEmpty(inactiveMembers)) {
            return false;
        }
        List<Long> activateMember = new ArrayList<>();
        List<Long> delMember = new ArrayList<>();
        // Get the ID of all spaces of the user
        List<String> spaceIds = iMemberService.getSpaceIdByUserId(userId);
        inactiveMembers.forEach(member -> {
            if (spaceIds.contains(member.getSpaceId())) {
                // An inactive member already exists in the user space. Delete the inactive member
                delMember.add(member.getId());
            }
            else {
                activateMember.add(member.getId());
            }
        });
        if (CollUtil.isNotEmpty(activateMember)) {
            // Activate members of the invited space and synchronize user information
            UserEntity entity = getById(userId);
            if (entity != null) {
                List<MemberEntity> updateMembers = new ArrayList<>();
                activateMember.forEach(memberId -> {
                    MemberEntity updateMember = new MemberEntity();
                    updateMember.setId(memberId);
                    updateMember.setUserId(userId);
                    updateMember.setMemberName(entity.getNickName());
                    updateMember.setMobile(entity.getMobilePhone());
                    updateMember.setEmail(entity.getEmail());
                    updateMember.setIsActive(true);
                    updateMembers.add(updateMember);
                });
                iMemberService.updateBatchById(updateMembers);
            }
        }
        // Delete duplicate inactive members of the same space
        if (CollUtil.isNotEmpty(delMember)) {
            iMemberService.removeByMemberIds(delMember);
        }
        return true;
    }

    private String getRandomAvatar() {
        String defaultAvatarList = constProperties.getDefaultAvatarList();
        if (StrUtil.isBlank(defaultAvatarList)) {
            return null;
        }
        String[] splits = defaultAvatarList.split(",");
        if (splits.length == 0) {
            return null;
        }
        return splits[RandomUtil.randomInt(0, splits.length)];
    }

    @Override
    public List<UserLangDTO> getLangByEmails(String expectedLang, List<String> emails) {
        // Maybe have performance problems, the segmented query is used.
        List<UserLangDTO> userLangs = new ArrayList<>(emails.size());
        int page = PageUtil.totalPage(emails.size(), QUERY_LOCALE_IN_EMAILS_LIMIT);
        for (int i = 0; i < page; i++) {
            List<String> subEmails = CollUtil.page(i, QUERY_LOCALE_IN_EMAILS_LIMIT, emails);
            userLangs.addAll(userMapper.selectLocaleInEmailsWithDefaultLocale(expectedLang, subEmails));
        }
        // Add an email that is not in the database
        if (userLangs.size() != emails.size()) {
            // Generally, they will not enter this judgment
            List<String> existEmails = userLangs.stream().map(UserLangDTO::getEmail).collect(Collectors.toList());
            List<String> nonExistEmails = CollUtil.subtractToList(emails, existEmails);
            nonExistEmails.forEach(email -> {
                UserLangDTO userLangDTO = new UserLangDTO();
                userLangDTO.setLocale(expectedLang);
                userLangDTO.setEmail(email);
                userLangs.add(userLangDTO);
            });
        }
        return userLangs;
    }

    @Override
    public String getLangByEmail(String expectedLang, String email) {
        UserLangDTO userLangDTO = userMapper.selectLocaleByEmailWithDefaultLocale(expectedLang, email);
        if (ObjectUtil.isNotNull(userLangDTO)) {
            return userLangDTO.getLocale();
        }
        return expectedLang;
    }

    @Override
    public List<UserLangDTO> getLangAndEmailByIds(List<Long> userIds, String defaultLocale) {
        List<UserLangDTO> dtos = userMapper.selectLocaleAndEmailByIds(userIds);
        return dtos.stream().peek(v -> {
            if (StrUtil.isBlank(v.getLocale())) {
                v.setLocale(defaultLocale);
            }
        }).collect(Collectors.toList());
    }

    @Override
    public void useInviteCodeReward(Long userId, String inviteCode) {
        // Query the user's invitation code, and determine that your own invitation code is not available
        String userInviteCode = ivCodeService.getUserInviteCode(userId);
        ExceptionUtil.isFalse(inviteCode.equals(userInviteCode), VCodeException.INVITE_CODE_NOT_VALID);
        // Users have not used invitation rewards
        boolean usedInviteReward = iIntegralService.checkByUserIdAndActionCodes(userId,
                CollectionUtil.newArrayList(IntegralActionCodeConstants.BE_INVITED_TO_REWARD, IntegralActionCodeConstants.OFFICIAL_INVITATION_REWARD));
        ExceptionUtil.isFalse(usedInviteReward, VCodeException.INVITE_CODE_REWARD_ERROR);
        // Load user information
        LoginUserDto userDto = loginUserService.getLoginUser(userId);
        // Save the use record of invitation code
        ivCodeService.useInviteCode(userId, userDto.getNickName(), inviteCode);
        // Query the user of the invitation code. If it does not exist, it represents the official invitation code
        Long inviteUserId = vCodeMapper.selectRefIdByCodeAndType(inviteCode, VCodeType.PERSONAL_INVITATION_CODE.getType());
        if (inviteUserId == null) {
            // Non personal invitation code, official invitation code
            iAuthService.officialInvitedReward(userId);
            return;
        }
        iAuthService.personalInvitedReward(userId, userDto.getNickName(), inviteUserId);
    }

    @Override
    public Long getUserIdByUuid(String uuid) {
        return userMapper.selectIdByUuid(uuid);
    }

    private String nullToDefaultNickName(String nickName) {
        return StrUtil.blankToDefault(nickName, String.format("星球居民%s", RandomExtendUtil.randomString(4)));
    }

    private String nullToDefaultAvatar(String avatar) {
        return StrUtil.blankToDefault(avatar, getRandomAvatar());
    }

    /**
     * Query users by user name
     * User's name can be email or area code+mobile phone number
     *
     * @param areaCode Area code
     * @param username User name
     * @return UserEntity
     */
    @Override
    public UserEntity getByUsername(String areaCode, String username) {
        if (Validator.isEmail(username)) {
            // Judge whether it exists
            UserEntity user = this.getByEmail(username);
            ExceptionUtil.isNotNull(user, USERNAME_OR_PASSWORD_ERROR);
            return user;
        }
        else if (StrUtil.isNotBlank(areaCode)) {
            ExceptionUtil.isTrue(Validator.isNumber(username), USERNAME_OR_PASSWORD_ERROR);
            // Judge whether it exists
            UserEntity user = this.getByCodeAndMobilePhone(areaCode, username);
            ExceptionUtil.isNotNull(user, USERNAME_OR_PASSWORD_ERROR);
            return user;
        }
        else {
            // User name format error
            throw new BusinessException(USERNAME_OR_PASSWORD_ERROR);
        }
    }
}
