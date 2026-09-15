package com.chengqu.huzhu.student.service;

import com.chengqu.huzhu.common.exception.BizException;
import com.chengqu.huzhu.common.file.LocalFileUrls;
import com.chengqu.huzhu.security.SecurityUtils;
import com.chengqu.huzhu.student.dto.StudentVerificationRequest;
import com.chengqu.huzhu.student.dto.StudentVerificationResponse;
import com.chengqu.huzhu.student.dto.StudentVerificationStatusResponse;
import com.chengqu.huzhu.student.entity.StudentVerification;
import com.chengqu.huzhu.student.entity.VerificationStatus;
import com.chengqu.huzhu.student.repository.StudentVerificationRepository;
import com.chengqu.huzhu.student.support.CampusCatalog;
import com.chengqu.huzhu.user.entity.User;
import com.chengqu.huzhu.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 学生认证：提交 → 管理员审核。
 *
 * <p>这条链路存在的唯一目的是把「在校学生」从一个用户自己勾的复选框，
 * 变成有凭证、有人核对之后才成立的结论。因此有两条不可让步的规则：
 * <ul>
 *   <li>{@code user.student} 只由 {@link #approve} 置真，用户在任何地方都改不了它
 *       （资料编辑里的那个字段已随本次改造删除）。</li>
 *   <li>同一用户同时至多一条 {@code PENDING}。表上没有唯一约束（会挡住第二条
 *       REJECTED 历史），所以这条规则由 {@link #submit} 的「先查后写」保证。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentVerificationService {

    /**
     * 「从未提交过」的哨兵值。
     *
     * <p>它不是 {@link VerificationStatus} 的成员，只出现在响应里：表里没有行就是
     * 没有行，为了一个状态值往表里插占位记录，会让重新提交和审核列表都要额外排除它。
     */
    public static final String STATUS_NONE = "NONE";

    /** NONE 的中文名，与各状态自己的 label 保持同一套口径 */
    private static final String LABEL_NONE = "未认证";

    private final StudentVerificationRepository verificationRepository;
    private final UserRepository userRepository;

    // ------------------------------------------------------------------
    // 用户侧
    // ------------------------------------------------------------------

    @Transactional
    public StudentVerificationResponse submit(StudentVerificationRequest request) {
        Long me = SecurityUtils.currentUser().getId();
        User user = requireCurrentUser(me);

        // 先看「已经是学生了没有」。这一条不查申请表，顺带也说明：
        // 认证通过之后用户无法靠再交一份申请来刷新学校信息。
        if (Boolean.TRUE.equals(user.getStudent())) {
            throw new BizException(400, "你已通过学生认证");
        }
        if (verificationRepository.existsByUserIdAndStatus(me, VerificationStatus.PENDING)) {
            throw new BizException(400, "已有待审核的认证申请，请等待管理员处理");
        }

        StudentVerification verification = StudentVerification.builder()
                .userId(me)
                .status(VerificationStatus.PENDING)
                .build();
        applyRequest(verification, request);
        StudentVerification saved = verificationRepository.save(verification);
        log.info("[学生认证] 提交申请 id={} userId={} school={}", saved.getId(), me, saved.getSchool());
        return StudentVerificationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public StudentVerificationStatusResponse mine() {
        Long me = SecurityUtils.currentUser().getId();
        User user = requireCurrentUser(me);
        StudentVerification latest = verificationRepository.findFirstByUserIdOrderByIdDesc(me).orElse(null);
        VerificationStatus status = latest == null ? null : latest.getStatus();
        log.info("[学生认证] 查询我的状态 userId={} status={}", me, status == null ? STATUS_NONE : status.name());
        return StudentVerificationStatusResponse.builder()
                .status(status == null ? STATUS_NONE : status.name())
                .statusLabel(status == null ? LABEL_NONE : status.getLabel())
                .verification(latest == null ? null : StudentVerificationResponse.from(latest))
                .student(Boolean.TRUE.equals(user.getStudent()))
                .build();
    }

    /**
     * 修改并重新提交。
     *
     * <p>被驳回的记录改完回到 {@code PENDING}，**原来那条驳回意见与审核时间必须清空**：
     * 它们是针对旧内容的，留着会让用户以为「这次交的还是上次说的那个问题」。
     *
     * <p>已通过的记录不允许再改：它已经换来了 {@code student = true}，
     * 改它等于绕过审核改一个已经生效的结论。学校信息变了应当另走一次申请，
     * 而不是在旧结论上打补丁。
     */
    @Transactional
    public StudentVerificationResponse update(Long id, StudentVerificationRequest request) {
        Long me = SecurityUtils.currentUser().getId();
        StudentVerification verification = requireOwn(id, me);
        if (verification.getStatus() == VerificationStatus.APPROVED) {
            throw new BizException("已通过的认证不能修改");
        }
        VerificationStatus previous = verification.getStatus();
        applyRequest(verification, request);
        verification.setStatus(VerificationStatus.PENDING);
        verification.setReviewNote(null);
        verification.setReviewedAt(null);
        StudentVerification saved = verificationRepository.save(verification);
        log.info("[学生认证] 重新提交 id={} userId={} 原状态={}", id, me, previous);
        return StudentVerificationResponse.from(saved);
    }

    /** 撤回自己的待审申请：只删待审的，已出结果的记录要留给用户看驳回原因 */
    @Transactional
    public void withdraw(Long id) {
        Long me = SecurityUtils.currentUser().getId();
        StudentVerification verification = requireOwn(id, me);
        if (verification.getStatus() != VerificationStatus.PENDING) {
            throw new BizException("已审核的认证申请不能撤回");
        }
        verificationRepository.delete(verification);
        log.info("[学生认证] 撤回申请 id={} userId={}", id, me);
    }

    // ------------------------------------------------------------------
    // 管理侧
    // ------------------------------------------------------------------

    /**
     * 审核列表：{@code status} 为空表示全部，排序一律按提交时间升序（先来先审）。
     *
     * <p>不做分页：这是一条纯人工的队列，管理员每天要处理的量级是几十条，
     * 真正需要的是「一眼看完全部待审」而不是翻页。等量级上来了再加 Pageable，
     * 现在加只会让前端多写一套分页逻辑。
     */
    @Transactional(readOnly = true)
    public List<StudentVerificationResponse> list(VerificationStatus status) {
        List<StudentVerification> list = status == null
                ? verificationRepository.findAllByOrderByCreatedAtAsc()
                : verificationRepository.findByStatusOrderByCreatedAtAsc(status);
        Map<Long, User> applicants = applicants(list);
        log.info("[学生认证] 管理端列表 status={} count={}", status == null ? "全部" : status, list.size());
        return list.stream()
                .map(item -> StudentVerificationResponse.of(item, applicants.get(item.getUserId())))
                .toList();
    }

    /**
     * 通过：这一步才真正授予学生身份。
     *
     * <p>{@code student = true} 与学校信息写回用户资料必须在同一个事务里完成：
     * 只置 student 会让校园模块看到「是在校学生但没有学校」，
     * 只写资料则等于没认证，两者隔开都会留下自相矛盾的中间态。
     */
    @Transactional
    public StudentVerificationResponse approve(Long id, String note) {
        StudentVerification verification = requirePending(id);
        verification.setStatus(VerificationStatus.APPROVED);
        verification.setReviewNote(blankToNull(note));
        verification.setReviewedAt(LocalDateTime.now());
        StudentVerification saved = verificationRepository.save(verification);

        User applicant = requireApplicant(saved.getUserId());
        applicant.setStudent(true);
        // 学校必填，直接覆盖：认证表单是学校信息的唯一可信来源
        applicant.setSchool(saved.getSchool());
        // 专业与年级选填，空值不覆盖用户资料里已经填过的内容——
        // 否则用户只是没在认证表单里重复填一遍，就会把资料里的专业抹掉
        if (StringUtils.hasText(saved.getMajor())) {
            applicant.setMajor(saved.getMajor().trim());
        }
        if (StringUtils.hasText(saved.getGrade())) {
            applicant.setGrade(saved.getGrade().trim());
        }
        // 姓名只补空：资料里已有名字时不覆盖，用户可能就用的是自己在用的名字
        if (!StringUtils.hasText(applicant.getRealName())) {
            applicant.setRealName(saved.getRealName());
        }
        userRepository.save(applicant);

        log.info("[学生认证] 审核通过 id={} userId={} school={}", id, saved.getUserId(), saved.getSchool());
        return StudentVerificationResponse.of(saved, applicant);
    }

    @Transactional
    public StudentVerificationResponse reject(Long id, String note) {
        StudentVerification verification = requirePending(id);
        verification.setStatus(VerificationStatus.REJECTED);
        verification.setReviewNote(blankToNull(note));
        verification.setReviewedAt(LocalDateTime.now());
        StudentVerification saved = verificationRepository.save(verification);
        // 驳回不改 user.student：驳回的是这一份申请，不是把用户已通过的身份收回去
        User applicant = requireApplicant(saved.getUserId());
        log.info("[学生认证] 审核驳回 id={} userId={} reason={}", id, saved.getUserId(), saved.getReviewNote());
        return StudentVerificationResponse.of(saved, applicant);
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    /** 把请求体写进实体；提交与重新提交共用一套取值规则 */
    private void applyRequest(StudentVerification verification, StudentVerificationRequest request) {
        verification.setRealName(request.getRealName().trim());
        verification.setSchool(CampusCatalog.requireSchool(request.getSchool()));
        verification.setMajor(CampusCatalog.optionalMajor(request.getMajor()));
        verification.setGrade(CampusCatalog.optionalGrade(request.getGrade()));
        verification.setStudentNo(request.getStudentNo().trim());
        verification.setProofImage(resolveProof(request.getProofImage()));
    }

    /**
     * 证件照与本项目其他图片同一条规矩：只收本站相对路径。
     *
     * <p>非本站地址直接报错而不是静默丢弃：用户以为传上去了、管理员却看到一片空白，
     * 比当场告诉他「先上传到本站」更糟。空字符串表示没传，是合法输入。
     */
    private static String resolveProof(String proofImage) {
        if (!StringUtils.hasText(proofImage)) {
            return null;
        }
        String local = LocalFileUrls.sanitizeOptional(proofImage);
        if (local == null) {
            throw new BizException("证件照必须先上传到本站");
        }
        return local;
    }

    /**
     * 取自己的某条申请。
     *
     * <p>不是本人的一律按「不存在」处理（404），不用 403：
     * 403 等于承认「这个 id 确实有申请」，别人可以拿它逐个探测。
     * 这与 AdService.withdraw 是同一条约定。
     */
    private StudentVerification requireOwn(Long id, Long userId) {
        return verificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BizException(404, "认证申请不存在"));
    }

    private StudentVerification requirePending(Long id) {
        StudentVerification verification = verificationRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "认证申请不存在"));
        if (verification.getStatus() != VerificationStatus.PENDING) {
            throw new BizException("这条申请已经审核过了");
        }
        return verification;
    }

    /**
     * 当前登录用户对应的库中记录。
     *
     * <p>查不到时给 401 而不是 404：令牌有效却查不到本人，说明令牌与库不一致
     * （用户已被删除或换了库），前端应该重新登录，而不是当成「资源不存在」。
     */
    private User requireCurrentUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BizException(401, "用户不存在"));
    }

    /** 管理员审核时的申请人；账号已不存在就没什么可授的了，按 404 报出来 */
    private User requireApplicant(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BizException(404, "申请人不存在"));
    }

    /**
     * 批量取申请人，供管理端列表一次性装配昵称与手机号。
     *
     * <p>申请与用户同库同服务，逐行 findById 是最典型的 N+1：一页几十条就是几十次查询。
     * 这里整页固定 1 次查询（findAllById），与条数无关；查不到的 id 缺席，
     * 由 DTO 回落成 null，不因为一个被删的账号让整页报错。
     */
    private Map<Long, User> applicants(List<StudentVerification> list) {
        List<Long> ids = list.stream()
                .map(StudentVerification::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, User> result = new HashMap<>();
        for (User user : userRepository.findAllById(ids)) {
            result.put(user.getId(), user);
        }
        return result;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
