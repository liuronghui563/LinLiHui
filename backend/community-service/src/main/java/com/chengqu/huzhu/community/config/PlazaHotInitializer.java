package com.chengqu.huzhu.community.config;

import com.chengqu.huzhu.community.service.PlazaHotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlazaHotInitializer implements ApplicationRunner {

    private final PlazaHotService plazaHotService;

    @Override
    public void run(ApplicationArguments args) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        if (plazaHotService.currentBoard().getItems().isEmpty()) {
            plazaHotService.refresh(today);
            log.info("[广场] 启动时生成当日热点榜");
        }
    }
}
