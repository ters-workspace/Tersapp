package org.example.tears.Model;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class TimeUtil {

    private static final ZoneId RIYADH_ZONE =
            ZoneId.of("Asia/Riyadh");

    public static LocalDateTime now() {
        return LocalDateTime.now(RIYADH_ZONE);
    }
}