package com.jinny.crypto.job;

import com.alibaba.fastjson2.JSON;
import com.jinny.crypto.common.vo.WalletBundle;
import com.jinny.crypto.utils.GenUtil;
import com.jinny.crypto.utils.TronGridBalance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
public class ScanJob {


    @Scheduled(fixedRate = 1000)
    public void exec() throws Exception {
        WalletBundle bundle = GenUtil.genAddress();

        TronGridBalance.Balances balances = TronGridBalance.fetchBalances(bundle.getTronAddress());
        if (balances.trx.compareTo(BigDecimal.valueOf(0.000001)) > 0 || balances.usdt.compareTo(BigDecimal.valueOf(0.01)) > 0) {
            log.info("中奖: {}", JSON.toJSONString(bundle));
        }
    }

}
