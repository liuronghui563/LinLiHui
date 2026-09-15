package com.chengqu.huzhu.student.service;

import com.chengqu.huzhu.common.exception.BizException;
import com.chengqu.huzhu.security.LoginUser;
import com.chengqu.huzhu.student.controller.AdminStudentVerificationController;
import com.chengqu.huzhu.student.dto.StudentVerificationRequest;
import com.chengqu.huzhu.student.dto.StudentVerificationResponse;
import com.chengqu.huzhu.student.dto.StudentVerificationStatusResponse;
import com.chengqu.huzhu.student.entity.StudentVerification;
import com.chengqu.huzhu.student.entity.VerificationStatus;
import com.chengqu.huzhu.student.repository.StudentVerificationRepository;
import com.chengqu.huzhu.user.entity.RoleType;
import com.chengqu.huzhu.user.entity.User;
import com.chengqu.huzhu.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 学生认证状态机测试。
 *
 * <p>这条链路的正确性全在**状态与越权**上，而不是在数据库里：
 * 「谁能提交、谁能改、通过之后用户资料变成什么样」都在服务层判定，
 * 因此这里以仓储的返回值为输入，逐条钉死这些结论。
 *
 * <p>只 mock 接口（两个 Repository），不 mock 实体与 DTO：
 * 本仓库的 MockMaker 是 mock-maker-subclass，对 final 类不适用。
 */
@ExtendWith(MockitoExtension.class)
class StudentVerificationServiceTest {

    private static final Long ME = 7L;

    private static final Long OTHER = 8L;

    @Mock
    private StudentVerificationRepository verificationRepository;
    @Mock
    private UserRepository userRepository;

    private StudentVerificationService service;

    @BeforeEach
    void setUp() {
        service = new StudentVerificationService(verificationRepository, userRepository);
        loginAs(ME);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------
    // 提交
    // ------------------------------------------------------------------

    @Test
    @DisplayName("提交：落库为 PENDING，用户端视图不带申请人信息")
    void submitStoresPending() {
        when(userRepository.findById(ME)).thenReturn(Optional.of(user(ME, false)));
        when(verificationRepository.existsByUserIdAndStatus(ME, VerificationStatus.PENDING)).thenReturn(false);
        when(verificationRepository.save(any(StudentVerification.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentVerificationRequest request = request();
        request.setProofImage("/api/file/objects/proof.png");
        StudentVerificationResponse response = service.submit(request);

        assertEquals(ME, response.getUserId());
        assertEquals(VerificationStatus.PENDING, response.getStatus());
        assertEquals("待审核", response.getStatusLabel());
        // 首尾空格在入库前就被裁掉，避免「张三 」与「张三」被当成两个人
        assertEquals("张三", response.getRealName());
        assertEquals("城区大学", response.getSchool());
        assertEquals("2023001", response.getStudentNo());
        assertEquals("/api/file/objects/proof.png", response.getProofImage());
        assertNull(response.getReviewNote());
        assertNull(response.getApplicantNickname(), "申请人信息只在管理端视图里给");
    }

    @Test
    @DisplayName("提交：已有 PENDING 时拒绝，并且不写库")
    void submitRejectsDuplicatePending() {
        when(userRepository.findById(ME)).thenReturn(Optional.of(user(ME, false)));
        when(verificationRepository.existsByUserIdAndStatus(ME, VerificationStatus.PENDING)).thenReturn(true);

        BizException ex = assertThrows(BizException.class, () -> service.submit(request()));

        assertEquals(400, ex.getCode());
        assertEquals("已有待审核的认证申请，请等待管理员处理", ex.getMessage());
        verify(verificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("提交：已通过认证的用户再次提交被拒绝")
    void submitRejectsVerifiedUser() {
        when(userRepository.findById(ME)).thenReturn(Optional.of(user(ME, true)));

        BizException ex = assertThrows(BizException.class, () -> service.submit(request()));

        assertEquals(400, ex.getCode());
        assertEquals("你已通过学生认证", ex.getMessage());
        verify(verificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("提交：证件照只收本站相对路径，外链当场拒绝而不是静默丢弃")
    void submitRejectsForeignProofImage() {
        when(userRepository.findById(ME)).thenReturn(Optional.of(user(ME, false)));
        when(verificationRepository.existsByUserIdAndStatus(ME, VerificationStatus.PENDING)).thenReturn(false);
        StudentVerificationRequest request = request();
        request.setProofImage("https://picsum.photos/200");

        BizException ex = assertThrows(BizException.class, () -> service.submit(request));

        assertEquals("证件照必须先上传到本站", ex.getMessage());
        verify(verificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("提交：令牌有效但库里没有这个人时给 401，而不是当成资源不存在")
    void submitRejectsUnknownCurrentUser() {
        when(userRepository.findById(ME)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> service.submit(request()));

        assertEquals(401, ex.getCode());
    }

    // ------------------------------------------------------------------
    // 我的认证状态
    // ------------------------------------------------------------------

    @Test
    @DisplayName("我的状态：从未提交过时状态是哨兵值 NONE，verification 为 null")
    void mineReturnsNoneWhenNeverSubmitted() {
        when(userRepository.findById(ME)).thenReturn(Optional.of(user(ME, false)));
        when(verificationRepository.findFirstByUserIdOrderByIdDesc(ME)).thenReturn(Optional.empty());

        StudentVerificationStatusResponse mine = service.mine();

        assertEquals(StudentVerificationService.STATUS_NONE, mine.getStatus());
        assertEquals("未认证", mine.getStatusLabel());
        assertNull(mine.getVerification());
        assertFalse(mine.getStudent());
    }

    @Test
    @DisplayName("我的状态：反映最近一条申请，驳回意见原样带回")
    void mineReflectsLatestRejection() {
        when(userRepository.findById(ME)).thenReturn(Optional.of(user(ME, false)));
        when(verificationRepository.findFirstByUserIdOrderByIdDesc(ME))
                .thenReturn(Optional.of(verification(3L, VerificationStatus.REJECTED, "照片模糊，请重新上传")));

        StudentVerificationStatusResponse mine = service.mine();

        assertEquals("REJECTED", mine.getStatus());
        assertEquals("已驳回", mine.getStatusLabel());
        assertEquals("照片模糊，请重新上传", mine.getVerification().getReviewNote());
        assertFalse(mine.getStudent());
    }

    @Test
    @DisplayName("我的状态：student 取库里那个字段，而不是「状态等于 APPROVED」")
    void mineReportsStudentFlagFromUserRow() {
        when(userRepository.findById(ME)).thenReturn(Optional.of(user(ME, true)));
        when(verificationRepository.findFirstByUserIdOrderByIdDesc(ME))
                .thenReturn(Optional.of(verification(3L, VerificationStatus.APPROVED, null)));

        StudentVerificationStatusResponse mine = service.mine();

        assertEquals("APPROVED", mine.getStatus());
        assertTrue(mine.getStudent());
    }

    // ------------------------------------------------------------------
    // 修改重新提交 / 撤回
    // ------------------------------------------------------------------

    @Test
    @DisplayName("驳回后重新提交：状态回到 PENDING，审核意见与审核时间被清空")
    void resubmitClearsReviewNoteAndReviewedAt() {
        StudentVerification rejected = verification(3L, VerificationStatus.REJECTED, "照片模糊");
        assertNotNull(rejected.getReviewedAt(), "前提：驳回记录带着审核时间");
        when(verificationRepository.findByIdAndUserId(3L, ME)).thenReturn(Optional.of(rejected));
        when(verificationRepository.save(any(StudentVerification.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentVerificationResponse response = service.update(3L, request());

        assertEquals(VerificationStatus.PENDING, response.getStatus());
        assertNull(response.getReviewNote(), "上次的驳回意见针对的是旧内容，必须清掉");
        assertNull(response.getReviewedAt());
        assertEquals("城区大学", response.getSchool());
    }

    @Test
    @DisplayName("已通过的认证不能修改")
    void updateRejectsApproved() {
        when(verificationRepository.findByIdAndUserId(3L, ME))
                .thenReturn(Optional.of(verification(3L, VerificationStatus.APPROVED, null)));

        BizException ex = assertThrows(BizException.class, () -> service.update(3L, request()));

        assertEquals("已通过的认证不能修改", ex.getMessage());
        verify(verificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("改别人的申请：返回 404 而不是 403，避免用 id 探测别人的申请是否存在")
    void updateForeignRecordReturns404() {
        when(verificationRepository.findByIdAndUserId(99L, ME)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> service.update(99L, request()));

        assertEquals(404, ex.getCode(), "403 等于承认这个 id 确实有申请");
        assertEquals("认证申请不存在", ex.getMessage());
    }

    @Test
    @DisplayName("撤回：只删自己的待审记录")
    void withdrawDeletesOwnPending() {
        when(verificationRepository.findByIdAndUserId(3L, ME))
                .thenReturn(Optional.of(verification(3L, VerificationStatus.PENDING, null)));

        service.withdraw(3L);

        verify(verificationRepository).delete(any(StudentVerification.class));
    }

    @Test
    @DisplayName("撤回别人的申请：同样按 404 处理")
    void withdrawForeignRecordReturns404() {
        when(verificationRepository.findByIdAndUserId(99L, ME)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> service.withdraw(99L));

        assertEquals(404, ex.getCode());
        verify(verificationRepository, never()).delete(any(StudentVerification.class));
    }

    @Test
    @DisplayName("撤回已出结果的申请：拒绝，记录要留给用户看驳回原因")
    void withdrawRejectsReviewed() {
        when(verificationRepository.findByIdAndUserId(3L, ME))
                .thenReturn(Optional.of(verification(3L, VerificationStatus.REJECTED, "照片模糊")));

        BizException ex = assertThrows(BizException.class, () -> service.withdraw(3L));

        assertEquals("已审核的认证申请不能撤回", ex.getMessage());
        verify(verificationRepository, never()).delete(any(StudentVerification.class));
    }

    // ------------------------------------------------------------------
    // 审核
    // ------------------------------------------------------------------

    @Test
    @DisplayName("审核通过：user.student 置 true，学校 / 专业 / 年级写回用户资料，姓名只补空")
    void approveGrantsStudentAndWritesBackProfile() {
        StudentVerification pending = verification(3L, VerificationStatus.PENDING, null);
        User applicant = user(ME, false);
        when(verificationRepository.findById(3L)).thenReturn(Optional.of(pending));
        when(verificationRepository.save(any(StudentVerification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(ME)).thenReturn(Optional.of(applicant));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentVerificationResponse response = service.approve(3L, "材料齐全");

        assertEquals(VerificationStatus.APPROVED, response.getStatus());
        assertEquals("材料齐全", response.getReviewNote());
        assertNotNull(response.getReviewedAt());
        assertEquals("小明", response.getApplicantNickname());
        assertEquals("13800000000", response.getApplicantPhone());

        assertTrue(applicant.getStudent(), "校园模块的门禁读的就是这个字段");
        assertEquals("城区大学", applicant.getSchool());
        assertEquals("计算机科学与技术", applicant.getMajor());
        assertEquals("大三", applicant.getGrade());
        assertEquals("张三", applicant.getRealName(), "原本为空，用认证表单里的姓名补上");
        verify(userRepository).save(applicant);
    }

    @Test
    @DisplayName("审核通过：用户资料里已有姓名时不覆盖")
    void approveKeepsExistingRealName() {
        User applicant = user(ME, false);
        applicant.setRealName("李四");
        when(verificationRepository.findById(3L))
                .thenReturn(Optional.of(verification(3L, VerificationStatus.PENDING, null)));
        when(verificationRepository.save(any(StudentVerification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(ME)).thenReturn(Optional.of(applicant));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.approve(3L, null);

        assertEquals("李四", applicant.getRealName());
    }

    @Test
    @DisplayName("审核通过：申请里没填的专业 / 年级不覆盖用户资料里已有的值")
    void approveKeepsProfileMajorAndGradeWhenApplicationBlank() {
        StudentVerification pending = StudentVerification.builder()
                .id(3L)
                .userId(ME)
                .realName("张三")
                .school("城区大学")
                .studentNo("2023001")
                .status(VerificationStatus.PENDING)
                .build();
        User applicant = user(ME, false);
        applicant.setMajor("电子信息");
        applicant.setGrade("大四");
        when(verificationRepository.findById(3L)).thenReturn(Optional.of(pending));
        when(verificationRepository.save(any(StudentVerification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(ME)).thenReturn(Optional.of(applicant));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.approve(3L, null);

        assertEquals("电子信息", applicant.getMajor(), "选填项为空不该把用户自己填过的资料抹掉");
        assertEquals("大四", applicant.getGrade());
        assertEquals("城区大学", applicant.getSchool());
    }

    @Test
    @DisplayName("驳回：写入审核意见与审核时间，且不动 user.student")
    void rejectWritesReviewNote() {
        when(verificationRepository.findById(3L))
                .thenReturn(Optional.of(verification(3L, VerificationStatus.PENDING, null)));
        when(verificationRepository.save(any(StudentVerification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(ME)).thenReturn(Optional.of(user(ME, false)));

        StudentVerificationResponse response = service.reject(3L, "  照片模糊，请重新上传  ");

        assertEquals(VerificationStatus.REJECTED, response.getStatus());
        assertEquals("照片模糊，请重新上传", response.getReviewNote());
        assertNotNull(response.getReviewedAt());
        assertEquals("小明", response.getApplicantNickname());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("驳回不带原因：审核意见落 null，不写入空串")
    void rejectWithoutNoteStoresNull() {
        when(verificationRepository.findById(3L))
                .thenReturn(Optional.of(verification(3L, VerificationStatus.PENDING, null)));
        when(verificationRepository.save(any(StudentVerification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(ME)).thenReturn(Optional.of(user(ME, false)));

        StudentVerificationResponse response = service.reject(3L, "   ");

        assertNull(response.getReviewNote());
    }

    @Test
    @DisplayName("重复审核：已出结果的申请不能再审一次")
    void approveRejectsAlreadyReviewed() {
        when(verificationRepository.findById(3L))
                .thenReturn(Optional.of(verification(3L, VerificationStatus.APPROVED, null)));

        BizException ex = assertThrows(BizException.class, () -> service.approve(3L, null));

        assertEquals("这条申请已经审核过了", ex.getMessage());
        verify(verificationRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("审核不存在的申请：404")
    void approveMissingRecordReturns404() {
        when(verificationRepository.findById(404L)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> service.approve(404L, null));

        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("通过时申请人已不存在：404，不写用户资料")
    void approveMissingApplicantReturns404() {
        when(verificationRepository.findById(3L))
                .thenReturn(Optional.of(verification(3L, VerificationStatus.PENDING, null)));
        when(verificationRepository.save(any(StudentVerification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(ME)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> service.approve(3L, null));

        assertEquals(404, ex.getCode());
        verify(userRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // 管理端列表
    // ------------------------------------------------------------------

    @Test
    @DisplayName("管理端列表：按仓储给的顺序（created_at 升序）返回，并批量补申请人昵称与手机号")
    void listEnrichesApplicantInfo() {
        StudentVerification first = verification(1L, VerificationStatus.PENDING, null);
        StudentVerification second = verification(2L, VerificationStatus.PENDING, null);
        second.setUserId(OTHER);
        User other = user(OTHER, false);
        other.setNickname("小红");
        other.setPhone("13900000000");
        when(verificationRepository.findByStatusOrderByCreatedAtAsc(VerificationStatus.PENDING))
                .thenReturn(List.of(first, second));
        when(userRepository.findAllById(anyList())).thenReturn(List.of(user(ME, false), other));

        List<StudentVerificationResponse> list = service.list(VerificationStatus.PENDING);

        assertEquals(2, list.size());
        assertEquals(ME, list.get(0).getUserId());
        assertEquals("小明", list.get(0).getApplicantNickname());
        assertEquals("13800000000", list.get(0).getApplicantPhone());
        assertEquals(OTHER, list.get(1).getUserId());
        assertEquals("小红", list.get(1).getApplicantNickname());
        // 整页只查一次用户表，不逐行查
        verify(userRepository, times(1)).findAllById(anyList());
    }

    @Test
    @DisplayName("管理端列表：不传 status 时返回全部")
    void listWithoutStatusReturnsAll() {
        when(verificationRepository.findAllByOrderByCreatedAtAsc()).thenReturn(List.of());

        assertTrue(service.list(null).isEmpty());
    }

    @Test
    @DisplayName("管理端列表接口：status 大小写 / 空格容错，拼错时给 400 而不是兜底 500")
    void listEndpointParsesStatusLeniently() {
        AdminStudentVerificationController controller = new AdminStudentVerificationController(service);
        when(verificationRepository.findByStatusOrderByCreatedAtAsc(VerificationStatus.PENDING))
                .thenReturn(List.of());

        assertTrue(controller.list(" pending ").getData().isEmpty());

        BizException ex = assertThrows(BizException.class, () -> controller.list("PENDNG"));
        assertEquals(400, ex.getCode());
        assertEquals("状态取值不合法，只能是 PENDING / APPROVED / REJECTED", ex.getMessage());
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    /** 以「我」的身份进入 SecurityContext，与服务里的 SecurityUtils.currentUser() 对齐 */
    private void loginAs(Long userId) {
        LoginUser loginUser = new LoginUser(userId, "13800000000", "小明", "hash", RoleType.USER, true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
    }

    private StudentVerificationRequest request() {
        StudentVerificationRequest request = new StudentVerificationRequest();
        request.setRealName(" 张三 ");
        request.setSchool(" 城区大学 ");
        request.setMajor("计算机科学与技术");
        request.setGrade("大三");
        request.setStudentNo(" 2023001 ");
        return request;
    }

    private StudentVerification verification(Long id, VerificationStatus status, String reviewNote) {
        return StudentVerification.builder()
                .id(id)
                .userId(ME)
                .realName("张三")
                .school("城区大学")
                .major("计算机科学与技术")
                .grade("大三")
                .studentNo("2023001")
                .status(status)
                .reviewNote(reviewNote)
                .reviewedAt(status == VerificationStatus.PENDING ? null : LocalDateTime.now())
                .build();
    }

    private User user(Long id, boolean student) {
        return User.builder()
                .id(id)
                .phone("13800000000")
                .nickname("小明")
                .passwordHash("hash")
                .role(RoleType.USER)
                .student(student)
                .build();
    }
}
