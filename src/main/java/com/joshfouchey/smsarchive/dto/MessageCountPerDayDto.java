package com.joshfouchey.smsarchive.dto;

import java.time.LocalDate;

public record MessageCountPerDayDto(
        LocalDate day,
        long count
) {}
