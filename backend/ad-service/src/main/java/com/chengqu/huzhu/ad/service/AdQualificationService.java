package com.chengqu.huzhu.ad.service;

import com.chengqu.huzhu.ad.dto.AdQualificationRequest;
import com.chengqu.huzhu.ad.dto.AdQualificationResponse;
import com.chengqu.huzhu.ad.dto.MyQualificationResponse;
import com.chengqu.huzhu.ad.entity.AdQualification;
import com.chengqu.huzhu.ad.entity.AdQualificationStatus;
import com.chengqu.huzhu.ad.repository.AdQualificationRepository;
import com.chengqu.huzhu.api.client.UserApiClient;
import com.chengqu.huzhu.api.dto.UserBrief;
import com.chengqu.huzhu.common.api.ApiResponse;
import com.chengqu.huzhu.common.exception.BizException;
import com.chengqu.huzhu.common.file.LocalFileUrls;
import com.chengqu.huzhu.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 广告位资质：提交 → 管理员审核 → 通过后才有资格提交广告位申请。
 *
 * <p>状态流转（同一行的三档，不是每次提交一条新记录）：
 * <pre>
 *   POST   /api/ad/qualification            → PENDING（新建一行）
 *   PUT    /api/ad/qualification/{id}       → PENDING（改内容并清掉上次审核意见）
 *   DELETE /api/ad/qualification/{id}       → 行被删除（仅限 PENDING）
 *                 ↓ 管理员
 *   POST   /api/ad/qualifications/{id}/approve → APPROVED
 *   POST   /api/ad/qualifications/{id}/reject  → REJECTED
 * </pre>
 *
 * <p>一个用户可以有多行资质，但至多一行 PENDING、至多一行 APPROVED：
 * REJECTED 的历史刻意保留（用户要看得到上次为什么被驳回），
 * 因此唯一性只能靠这里的方法判断，不能靠数据库唯一键
 * ——原因见 {@code V4__ad_qualification.sql} 的注释。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdQualificationService {

    private final AdQualificationRepository qualificationRepository;
    /** 管理端列表要显示账号昵称；依赖不可用时降级为只显示 id，不阻塞审核 */
    private final UserApiClient userApiClient;

    @Transactional
    public AdQualificationResponse submit(AdQualificationRequest request) {
        Long me = SecurityUtils.currentUser().getId();
        // 先答「已开通」：通过之后本来就不该再提交，但真出现这种调用时，
        // 告诉用户「你已经能投广告了」比「有审核中的申请」有用得多。
        if (qualificationRepository.existsByUserIdAndStatus(me, AdQualificationStatus.APPROVED)) {
            throw new BizException("你的广告位资质已开通");
        }
        if (qualificationRepository.existsByUserIdAndStatus(me, AdQualificationStatus.PENDING)) {
            throw new BizException("已有待审核的资质申请，请等待管理员处理");
        }
        AdQualification saved = qualificationRepository.save(AdQualification.builder()
                .userId(me)
                .applicantName(request.getApplicantName().trim())
                .contact(request.getContact().trim())
                .company(blankToNull(request.getCompany()))
                .category(blankToNull(request.getCategory()))
                .intro(blankToNull(request.getIntro()))
                .licenseImage(resolveLicenseImage(request.getLicenseImage()))
                .status(AdQualificationStatus.PENDING)
                .build());
        log.info("[资质] 收到申请 id={} userId={} company={}", saved.getId(), me, saved.getCompany());
        return AdQualificationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public MyQualificationResponse mine() {
        Long me = SecurityUtils.currentUser().getId();
        AdQualification latest =
                qualificationRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(me).orElse(null);
        MyQualificationResponse view = MyQualificationResponse.of(latest);
        log.info("[资质] 我的资质 userId={} status={}", me, view.getStatus());
        return view;
    }

    /**
     * 修改并重新提交：只能改自己的，且当前是 PENDING 或 REJECTED。
     *
     * <p>PENDING 也允许改，是刻意的：材料填错（比如电话写错一位）不该逼用户
     * 先撤回再重填一遍。改完仍是 PENDING，清掉 reviewNote / reviewedAt
     * ——它们是针对**旧材料**写的，留着会让用户以为新提交的内容已经带上了上次的意见。
     */
    @Transactional
    public AdQualificationResponse update(Long id, AdQualificationRequest request) {
        Long me = SecurityUtils.currentUser().getId();
        AdQualification qualification = requireOwned(id, me);
        if (qualification.getStatus() == AdQualificationStatus.APPROVED) {
            throw new BizException("资质已通过，无需修改");
        }
        qualification.setApplicantName(request.getApplicantName().trim());
        qualification.setContact(request.getContact().trim());
        qualification.setCompany(blankToNull(request.getCompany()));
        qualification.setCategory(blankToNull(request.getCategory()));
        qualification.setIntro(blankToNull(request.getIntro()));
        qualification.setLicenseImage(resolveLicenseImage(request.getLicenseImage()));
        qualification.setStatus(AdQualificationStatus.PENDING);
        qualification.setReviewNote(null);
        qualification.setReviewedAt(null);
        AdQualification saved = qualificationRepository.save(qualification);
        log.info("[资质] 修改并重新提交 id={} userId={}（回到待审）", id, me);
        return AdQualificationResponse.from(saved);
    }

    /**
     * 撤回：只删自己的、且仍在待审的那一行。
     *
     * <p>已通过的资质不给撤回：开通是管理员给出的结论，用户单方面取消
     * 会留下「广告还在轮播、资质却没了」的空档。真要停用应该走运营流程。
     */
    @Transactional
    public void withdraw(Long id) {
        Long me = SecurityUtils.currentUser().getId();
        AdQualification qualification = requireOwned(id, me);
        if (qualification.getStatus() != AdQualificationStatus.PENDING) {
            throw new BizException("只有待审核的资质申请可以撤回");
        }
        qualificationRepository.delete(qualification);
        log.info("[资质] 撤回申请 id={} userId={}", id, me);
    }

    /** 管理端：资质列表，按提交时间正序（先来先审），并批量补全账号昵称（一次 Feign，不逐行查） */
    @Transactional(readOnly = true)
    public List<AdQualificationResponse> list(AdQualificationStatus status) {
        List<AdQualification> list = status == null
                ? qualificationRepository.findAllByOrderByCreatedAtAsc()
                : qualificationRepository.findByStatusOrderByCreatedAtAsc(status);
        Map<Long, String> names = userNicknames(list);
        log.info("[资质] 管理列表 status={} count={}", status == null ? "全部" : status, list.size());
        return list.stream()
                .map(qualification -> AdQualificationResponse.of(qualification, names.get(qualification.getUserId())))
                .toList();
    }

    @Transactional
    public AdQualificationResponse approve(Long id, String note) {
        AdQualification qualification = requirePending(id);
        qualification.setStatus(AdQualificationStatus.APPROVED);
        qualification.setReviewNote(blankToNull(note));
        qualification.setReviewedAt(LocalDateTime.now());
        AdQualification saved = qualificationRepository.save(qualification);
        log.info("[资质] 审核通过 id={} userId={}（该用户现在可以提交广告位申请）", id, saved.getUserId());
        return AdQualificationResponse.from(saved);
    }

    @Transactional
    public AdQualificationResponse reject(Long id, String note) {
        AdQualification qualification = requirePending(id);
        qualification.setStatus(AdQualificationStatus.REJECTED);
        qualification.setReviewNote(blankToNull(note));
        qualification.setReviewedAt(LocalDateTime.now());
        AdQualification saved = qualificationRepository.save(qualification);
        log.info("[资质] 审核驳回 id={} userId={} reason={}", id, saved.getUserId(), saved.getReviewNote());
        return AdQualificationResponse.from(saved);
    }

    /**
     * 取自己的资质行。
     *
     * <p>不是本人时同样报 404「资质申请不存在」，而不是 403：
     * 403 等于承认「这个 id 是存在的、只是不属于你」，别人就能拿 id 递增探测
     * 平台上有多少条资质、什么时候提交的。沿用广告申请里的同一约定。
     */
    private AdQualification requireOwned(Long id, Long me) {
        AdQualification qualification = qualificationRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "资质申请不存在"));
        if (!me.equals(qualification.getUserId())) {
            throw new BizException(404, "资质申请不存在");
        }
        return qualification;
    }

    private AdQualification requirePending(Long id) {
        AdQualification qualification = qualificationRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "资质申请不存在"));
        if (qualification.getStatus() != AdQualificationStatus.PENDING) {
            throw new BizException("这条资质申请已经审核过了");
        }
        return qualification;
    }

    /**
     * 批量取账号昵称。
     *
     * <p>与 {@code AdService.applicantNames} 是同一套做法：整页只发一次 Feign、不逐行查；
     * 下游不可用就返回空 map，前端回落到「用户 {id}」而不是整页报错。
     * 这段重复是刻意接受的：抽成公共组件目前只服务两处，却要多一个注入点，
     * 等第三个调用方出现再抽更划算。
     */
    private Map<Long, String> userNicknames(List<AdQualification> qualifications) {
        List<Long> ids = qualifications.stream()
                .map(AdQualification::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            ApiResponse<List<UserBrief>> response = userApiClient.findBriefs(ids);
            if (response == null || response.getData() == null) {
                return Map.of();
            }
            return response.getData().stream()
                    .filter(brief -> brief.id() != null)
                    .collect(Collectors.toMap(UserBrief::id, brief -> brief.nickname() == null ? "" : brief.nickname(),
                            (a, b) -> a));
        } catch (Exception e) {
            log.warn("[资质] 补全账号昵称失败，降级为只显示 id：{}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 资质证明图必须是本站对象。
     *
     * <p>可空但**不能乱填**：空白视为没交（个人主体常常没有营业执照），
     * 填了外链则拒绝——本项目所有图片只存 {@code /api/file/objects/...} 相对路径，
     * 第三方图床的链接迟早会失效，而资质材料恰恰是要长期留档的东西。
     */
    private static String resolveLicenseImage(String licenseImage) {
        if (licenseImage == null || licenseImage.isBlank()) {
            return null;
        }
        String local = LocalFileUrls.sanitizeOptional(licenseImage);
        if (local == null) {
            throw new BizException("资质证明图必须先上传到本站");
        }
        return local;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
