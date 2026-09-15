package com.chengqu.huzhu.student.support;

import com.chengqu.huzhu.common.exception.BizException;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/**
 * 校园认证可选学校 / 专业 / 年级。
 *
 * <p>前端下拉框与后端白名单共用同一份清单，避免用户手填任意内容。
 * 学校与专业目前是演示数据，后续接入真实名录时只改这一处。
 */
public final class CampusCatalog {

    public static final List<String> SCHOOLS = List.of(
            "城区大学",
            "邻里职业技术学院",
            "城南师范学院"
    );

    public static final List<String> MAJORS = List.of(
            "计算机科学与技术",
            "电子信息工程",
            "工商管理",
            "汉语言文学",
            "护理学"
    );

    public static final List<String> GRADES = List.of(
            "大一", "大二", "大三", "大四",
            "研一", "研二", "研三"
    );

    private static final Set<String> SCHOOL_SET = Set.copyOf(SCHOOLS);
    private static final Set<String> MAJOR_SET = Set.copyOf(MAJORS);
    private static final Set<String> GRADE_SET = Set.copyOf(GRADES);

    private CampusCatalog() {
    }

    public static String requireSchool(String school) {
        String value = trim(school);
        if (value == null || !SCHOOL_SET.contains(value)) {
            throw new BizException("请从列表中选择学校");
        }
        return value;
    }

    public static String optionalMajor(String major) {
        return optionalOf(major, MAJOR_SET, "请从列表中选择专业");
    }

    public static String optionalGrade(String grade) {
        return optionalOf(grade, GRADE_SET, "请从列表中选择年级");
    }

    private static String optionalOf(String raw, Set<String> allowed, String message) {
        String value = trim(raw);
        if (value == null) {
            return null;
        }
        if (!allowed.contains(value)) {
            throw new BizException(message);
        }
        return value;
    }

    private static String trim(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
