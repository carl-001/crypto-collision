package com.jinny.crypto.job;

import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Maps;
import com.jinny.crypto.common.vo.WalletBundle;
import com.jinny.crypto.modules.crypto.entity.CryptoInfo;
import com.jinny.crypto.modules.crypto.mapper.CryptoInfoMapper;
import com.jinny.crypto.utils.EtherscanBalanceClient;
import com.jinny.crypto.utils.GenUtil;
import com.jinny.crypto.utils.TronGridBalance;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@AllArgsConstructor
public class ScanJob {

    private final CryptoInfoMapper cryptoInfoMapper;

    @Scheduled(fixedRate = 300)
    public void exec() throws Exception {
        WalletBundle bundle = GenUtil.genAddress();

        CompletableFuture.runAsync(() -> {
            TronGridBalance.Balances balances = null;
            try {
                balances = TronGridBalance.fetchBalances(bundle.getTronAddress());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            Map<String, BigDecimal> common = null;
            try {
                common = EtherscanBalanceClient.getBalancesByAddress(bundle.getEthAddress());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            boolean has = common.values().stream().anyMatch(v -> v.compareTo(BigDecimal.ZERO) > 0);

            if (balances.trx.compareTo(BigDecimal.valueOf(0.000001)) > 0 || balances.usdt.compareTo(BigDecimal.valueOf(0.01)) > 0 || has) {
                log.info("中奖: {}", JSON.toJSONString(bundle));

                HashMap<Object, Object> tronMap = Maps.newHashMap();
                tronMap.put("trx", balances.trx);
                tronMap.put("usdt", balances.usdt);

                CryptoInfo cryptoInfo = new CryptoInfo();
                BeanUtils.copyProperties(bundle, cryptoInfo);
                cryptoInfo.setMnemonic(JSON.toJSONString(bundle.getMnemonic()));
                cryptoInfo.setTronAmount(JSON.toJSONString(tronMap));
                cryptoInfo.setEthAmount(JSON.toJSONString(common));
                cryptoInfoMapper.insert(cryptoInfo);
            }
        });
    }

}
