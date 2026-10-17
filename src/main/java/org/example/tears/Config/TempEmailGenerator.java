package org.example.tears.Config;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Random;

@Component
public class TempEmailGenerator {
        private final Random random = new Random();

        public String generate(String fullName) {

            // إزالة المسافات الزائدة
            fullName = fullName.trim().replaceAll("\\s+", " ");

            String[] parts = fullName.split(" ");

            if (parts.length < 2) {
                throw new IllegalArgumentException("Full name must contain at least two parts");
            }

            // أول حرف من الأول والثاني
            String first = parts[0].substring(0, 1);
            String second = parts[1].substring(0, 1);

            // آخر اسم كامل
            String last = parts[parts.length - 1];

            // تحويل للإنجليزي لو كان عربي
            String base = toLatin(first + "." + second + "." + last);

            // رقم عشوائي للتفادي التكرار
            int num = random.nextInt(9000) + 1000;

            return base.toLowerCase() + "." + num + "@ters.app";
        }

        // تبسيط الأحرف (لو فيها تشكيل أو رموز)
        private String toLatin(String input) {
            return Normalizer.normalize(input, Normalizer.Form.NFD)
                    .replaceAll("[^\\p{ASCII}]", "")
                    .replaceAll("[^a-zA-Z.]", "");
        }
    }

