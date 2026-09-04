package com.qanoon.security;

import com.qanoon.common.BusinessException;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * سياسة كلمة المرور: ٨ أحرف على الأقل، حرف كبير، حرف صغير، رقم، ورمز خاص.
 * ترمي رسالة عربية تشرح الشرط الناقص تحديداً.
 */
@Component
public class PasswordPolicy {

    /** الحد الأدنى لطول كلمة المرور. */
    @Getter
    private final int minLength;

    public PasswordPolicy(@Value("${qanoon.password-min-length:8}") int minLength) {
        this.minLength = minLength >= 8 ? minLength : 8;
    }

    /** يتحقق من كلمة المرور ويرمي {@link BusinessException} عند مخالفة أي شرط. */
    public void validate(String password) {
        if (password == null || password.isBlank()) {
            throw new BusinessException("كلمة المرور مطلوبة");
        }
        if (password.contains(" ")) {
            throw new BusinessException("كلمة المرور يجب ألا تحتوي على مسافات");
        }
        if (password.length() < minLength) {
            throw new BusinessException("كلمة المرور قصيرة — يجب ألا تقل عن " + minLength + " أحرف");
        }

        boolean upper = false;
        boolean lower = false;
        boolean digit = false;
        boolean special = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                upper = true;
            } else if (c >= 'a' && c <= 'z') {
                lower = true;
            } else if (c >= '0' && c <= '9') {
                digit = true;
            } else {
                special = true;
            }
        }

        if (!upper) {
            throw new BusinessException("كلمة المرور يجب أن تحتوي على حرف إنجليزي كبير واحد على الأقل (A-Z)");
        }
        if (!lower) {
            throw new BusinessException("كلمة المرور يجب أن تحتوي على حرف إنجليزي صغير واحد على الأقل (a-z)");
        }
        if (!digit) {
            throw new BusinessException("كلمة المرور يجب أن تحتوي على رقم واحد على الأقل (0-9)");
        }
        if (!special) {
            throw new BusinessException("كلمة المرور يجب أن تحتوي على رمز خاص واحد على الأقل مثل @ # $ % !");
        }
    }

    /** نص الشروط لعرضه في الواجهة. */
    public String requirementsText() {
        return "كلمة المرور: " + minLength
                + " أحرف على الأقل، وتشمل حرفاً كبيراً وحرفاً صغيراً ورقماً ورمزاً خاصاً، بلا مسافات";
    }
}
