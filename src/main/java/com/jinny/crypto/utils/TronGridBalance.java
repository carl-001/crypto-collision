package com.jinny.crypto.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.var;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class TronGridBalance {

    // TronGrid 基础地址与 USDT(TRC20) 合约地址（主网）
    private static final String TRONGRID_BASE = "https://api.trongrid.io";
    private static final String USDT_CONTRACT = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t";

    // 写死的三个 API Key（替换成你的）
    private static final String[] RAW_KEYS = new String[] {
            "ff3468f1-bfad-4d98-aced-35375ae0223a",
            "da178cd7-9c43-4cb9-b2d4-8d4b878a2944",
            "784bd3f0-2cb0-4967-bd9d-85df003aade3"
    };

    private static final List<String> API_KEYS = new ArrayList<>();
    private static final AtomicInteger KEY_INDEX = new AtomicInteger(0);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final RequestConfig REQUEST_CONFIG = RequestConfig.custom()
            .setConnectTimeout(Timeout.ofSeconds(10))
            .setResponseTimeout(Timeout.ofSeconds(20))
            .build();

    private static final CloseableHttpClient HTTP = HttpClients.custom()
            .setDefaultRequestConfig(REQUEST_CONFIG)
            .build();

    static {
        for (String k : RAW_KEYS) {
            if (k != null ) {
                API_KEYS.add(k.trim());
            }
        }
        if (API_KEYS.isEmpty()) {
            throw new IllegalStateException("请在 RAW_KEYS 中配置至少一个有效的 TRON-PRO-API-KEY");
        }
    }
    public static class Balances {
        public final BigDecimal trx;   // 单位：TRX
        public final BigDecimal usdt;  // 单位：USDT
        public Balances(BigDecimal trx, BigDecimal usdt) {
            this.trx = trx; this.usdt = usdt;
        }
        @Override public String toString() {
            return "TRX=" + trx.stripTrailingZeros().toPlainString() +
                    ", USDT=" + usdt.stripTrailingZeros().toPlainString();
        }
    }

    private static String nextKey() {
        int idx = Math.floorMod(KEY_INDEX.getAndIncrement(), API_KEYS.size());
        return API_KEYS.get(idx);
    }

    public static Balances fetchBalances(String tronAddress) throws Exception {
        String url = TRONGRID_BASE + "/v1/accounts/" + tronAddress;

        // 最多尝试等于 key 数量的次数；每次轮询下一个 key
        Exception lastErr = null;
        for (int attempt = 0; attempt < API_KEYS.size(); attempt++) {
            String key = nextKey();
            HttpGet req = new HttpGet(url);
            req.addHeader("Accept", "application/json");
            req.addHeader("TRON-PRO-API-KEY", key);

            try (CloseableHttpResponse resp = HTTP.execute(req)) {
                int sc = resp.getCode();
                String body = resp.getEntity() != null
                        ? EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8)
                        : "";

                if (sc >= 200 && sc < 300) {
                    return parseBalances(body);
                }

                // 非 2xx：决定是否用下一个 key 重试
                if (isRetryable(sc)) {
                    lastErr = new RuntimeException("HTTP " + sc + " with key[" + mask(key) + "]: " + body);
                    // 简单退避
                    Thread.sleep(200L * (attempt + 1));
                    continue; // 尝试下一个 key
                } else {
                    throw new RuntimeException("HTTP " + sc + ": " + body);
                }
            } catch (IOException ioe) {
                lastErr = ioe;
                // I/O 异常也尝试下一个 key
                Thread.sleep(200L * (attempt + 1));
            }
        }
        throw lastErr != null ? lastErr : new RuntimeException("请求失败（未知原因）");
    }

    private static boolean isRetryable(int status) {
        // 429/5xx、以及常见鉴权问题时尝试下一个 key
        return status == 429 || status == 401 || status == 403 || (status >= 500 && status <= 599);
    }

    private static String mask(String key) {
        if (key.length() <= 6) return "***";
        return key.substring(0, 3) + "..." + key.substring(key.length() - 3);
    }

    private static Balances parseBalances(String json) throws IOException {
        JsonNode root = MAPPER.readTree(json);
        JsonNode data = root.path("data");
        if (!data.isArray() || data.size() == 0) {
            return new Balances(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        JsonNode account = data.get(0);

        // TRX：Sun -> TRX
        String balanceSunStr = account.path("balance").asText("0");
        BigDecimal trx = new BigDecimal(new BigInteger(balanceSunStr))
                .divide(new BigDecimal("1000000")); // 1 TRX = 1e6 Sun

        // USDT：从 trc20 列表找指定合约；返回整数 -> /1e6
        BigDecimal usdt = BigDecimal.ZERO;
        JsonNode trc20 = account.path("trc20");
        if (trc20.isArray()) {
            for (JsonNode tokenObj : trc20) {
                // 该数组每个元素是 {<contract>: "<amount>"} 这样的结构
                var fields = tokenObj.fields();
                while (fields.hasNext()) {
                    var e = fields.next();
                    String contract = e.getKey();
                    if (USDT_CONTRACT.equalsIgnoreCase(contract)) {
                        String raw = e.getValue().asText("0");
                        try {
                            usdt = new BigDecimal(new BigInteger(raw))
                                    .divide(new BigDecimal("1000000")); // USDT 在 TRON 上 6 位小数
                        } catch (NumberFormatException ignored) {
                            usdt = BigDecimal.ZERO;
                        }
                        break;
                    }
                }
            }
        }

        return new Balances(trx, usdt);
    }

    public static void test() throws Exception {
        Balances b = fetchBalances("TRUFNBWh3EuvsES6bdHMkSEXYDcUxLaJDa");
        System.out.println(b);
    }

    // 演示：运行时传入地址
    public static void main(String[] args) throws Exception {
        test();
    }

}
