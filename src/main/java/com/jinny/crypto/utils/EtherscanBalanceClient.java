package com.jinny.crypto.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.var;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class EtherscanBalanceClient {

    /* ======= 可直连的公共 RPC（国内可用），会自动切换 ======= */
    private static final List<String> RPCS = Lists.newArrayList(
            "https://ethereum.publicnode.com",
            "https://eth-mainnet.public.blastapi.io",
            "https://1rpc.io/eth"
    );

    private static final ObjectMapper M = new ObjectMapper();
    private static final CloseableHttpClient HTTP = HttpClients.custom()
            .setDefaultRequestConfig(RequestConfig.custom()
                    .setConnectTimeout(Timeout.ofSeconds(10))
                    .setResponseTimeout(Timeout.ofSeconds(20))
                    .build())
            .build();

    /* ====================== 你要的唯一公开方法 ====================== */
    public static Map<String, BigDecimal> getBalancesByAddress(String address) throws Exception {
        Map<String, String> COMMON_TOKENS = Maps.newHashMap();
        COMMON_TOKENS.put("ETH", "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2");
        COMMON_TOKENS.put("USDC", "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eb48");
        COMMON_TOKENS.put("DAI", "0x6B175474E89094C44Da98b954EedeAC495271d0F");
        COMMON_TOKENS.put("WETH", "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2");

        String addr = checksum(address);

        // 1) ETH
        BigDecimal eth = getEth(addr);
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        if (eth.compareTo(BigDecimal.ZERO) > 0) out.put("ETH", eth);

        // 2) 常见 ERC-20（非 0 才返回）
        for (var e : COMMON_TOKENS.entrySet()) {
            BigDecimal v = getErc20(addr, e.getValue());
            if (v.compareTo(BigDecimal.ZERO) > 0) {
                out.put(e.getKey(), v);
            }
        }
        // 若 ETH 也为 0，仍然把 ETH=0 返回，方便你区分“查到了只是 0”与“没查到”
        out.putIfAbsent("ETH", eth);
        return out;
    }
    /* ====================== ↑↑↑ 仅调用这一个 ↑↑↑ ====================== */


    /* -------------------- 下面是内部实现细节 -------------------- */

    private static BigDecimal getEth(String addr) throws Exception {
        String payload = rpc("eth_getBalance", "[\"" + addr + "\",\"latest\"]", 1);
        String hex = postWithFailover(payload);
        BigInteger wei = toBigInt(hex);
        return new BigDecimal(wei).divide(new BigDecimal("1000000000000000000")); // 1e18
    }

    private static BigDecimal getErc20(String holder, String token) throws Exception {
        // 为了避免节点不一致，balanceOf/decimals 尽量用同一个 RPC
        String rpc = pickRpc();

        // 1) balanceOf(address)
        String dataBal = "0x70a08231" + leftPad64(strip0x(holder));
        String hexAmt = postOn(rpc, rpcCall(token, dataBal, 2));
        if (isEmpty(hexAmt)) { // 某些公共节点会返回 "0x"
            rpc = pickAnother(rpc);
            hexAmt = postOn(rpc, rpcCall(token, dataBal, 22));
        }
        BigInteger raw = toBigInt(hexAmt);
        if (raw.signum() == 0) return BigDecimal.ZERO;

        // 2) decimals()
        String hexDec = postOn(rpc, rpcCall(token, "0x313ce567", 3));
        if (isEmpty(hexDec)) {
            rpc = pickAnother(rpc);
            hexDec = postOn(rpc, rpcCall(token, "0x313ce567", 33));
        }
        int decimals = toBigInt(hexDec).intValue();

        // 3) 换算
        BigDecimal base = BigDecimal.TEN.pow(decimals);
        return new BigDecimal(raw).divide(base);
    }

    /* ---------------- HTTP / JSON-RPC helpers ---------------- */

    private static String postWithFailover(String json) throws Exception {
        Exception last = null;
        int start = new Random().nextInt(RPCS.size());
        for (int i = 0; i < RPCS.size(); i++) {
            String rpc = RPCS.get((start + i) % RPCS.size());
            try {
                String r = postOn(rpc, json);
                if (!isEmpty(r)) return r;
                last = new RuntimeException("Empty result from " + rpc);
            } catch (Exception ex) {
                last = ex;
            }
        }
        throw last != null ? last : new RuntimeException("All RPCs failed");
    }

    private static String postOn(String rpcUrl, String json) throws Exception {
        HttpPost post = new HttpPost(rpcUrl);
        post.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
        try (var resp = HTTP.execute(post)) {
            String body = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
            if (resp.getCode() / 100 != 2) throw new RuntimeException("HTTP " + resp.getCode() + ": " + body);
            JsonNode root = M.readTree(body);
            if (root.has("error")) throw new RuntimeException("RPC error: " + root.get("error").toString());
            return root.path("result").asText("0x");
        }
    }

    /* ---------------- misc helpers ---------------- */

    private static String rpc(String method, String params, int id) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"" + method + "\",\"params\":" + params + "}";
    }

    private static String rpcCall(String to, String data, int id) {
        return rpc("eth_call", "[{\"to\":\"" + checksum(to) + "\",\"data\":\"" + data + "\"},\"latest\"]", id);
    }

    private static boolean isEmpty(String r) {
        return r == null || r.equalsIgnoreCase("0x");
    }

    private static String pickRpc() {
        return RPCS.get(new Random().nextInt(RPCS.size()));
    }

    private static String pickAnother(String current) {
        for (String r : RPCS) if (!Objects.equals(r, current)) return r;
        return current;
    }

    private static BigInteger toBigInt(String hex) {
        if (hex == null) return BigInteger.ZERO;
        String s = hex.startsWith("0x") ? hex.substring(2) : hex;
        return new BigInteger(s, 16);
    }

    private static String strip0x(String s) {
        return s.startsWith("0x") ? s.substring(2) : s;
    }

    private static String checksum(String addr) {
        return addr.startsWith("0x") ? addr : "0x" + addr;
    }

    private static String leftPad64(String hex) {
        String s = hex.toLowerCase();
        if (s.length() > 64) s = s.substring(s.length() - 64);
        StringBuilder sb = new StringBuilder(64);
        for (int i = s.length(); i < 64; i++) sb.append('0');
        return sb.append(s).toString();
    }

    public static boolean hasAnyCommonTokenBalance(Map<String, BigDecimal> common) {
        return common != null && common.values().stream().anyMatch(v -> v.compareTo(BigDecimal.ZERO) > 0);
    }

    /* ------------------------- Demo ------------------------- */
    public static void main(String[] args) throws Exception {
        Map<String, BigDecimal> common = getBalancesByAddress("0x8AB5C8DF2Df20EF9CfaC7af01A5299bb0Ac99058");
        boolean has = common.values().stream().anyMatch(v -> v.compareTo(BigDecimal.ZERO) > 0);
        System.out.println(common);
        System.out.println(has);
    }

}
