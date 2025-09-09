package com.jinny.crypto.utils;

import com.jinny.crypto.common.vo.WalletBundle;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.*;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bouncycastle.jcajce.provider.digest.Keccak;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

public class GenUtil {

    public static void main(String[] args) throws Exception {

        WalletBundle bundle = genAddress();

        // 打印
        System.out.println(bundle);
    }

    public static WalletBundle genAddress() throws Exception {
        // 1) 生成 12 词助记词
        List<String> mnemonic = generateUniqueMnemonic12();
        String passphrase = "";

        // 2) 从助记词得到 seed 与 BIP32 主键
        DeterministicKey master = masterFromMnemonic(mnemonic, passphrase);

        // 3) 统一封装到对象
        WalletBundle bundle = new WalletBundle();
        bundle.setMnemonic(mnemonic);

        // === BTC 地址 ===
        DeterministicKey btcKey = deriveBip44(master, 0 /*BTC*/, 0, 0, 0);
        bundle.setBtcAddress(btcP2pkhAddress(btcKey));
        bundle.setBtcPrivateKey(btcKey.getPrivateKeyAsWiF(NetworkParameters.fromID(NetworkParameters.ID_MAINNET)));

        // === ETH 地址 ===
        DeterministicKey ethKey = deriveBip44(master, 60 /*ETH*/, 0, 0, 0);
        bundle.setEthAddress(ethChecksumAddress(ethKey));
        bundle.setEthPrivateKey(ethKey.getPrivKey().toString(16));

        // === TRON 地址 ===
        DeterministicKey tronKey = deriveBip44(master, 195 /*TRON*/, 0, 0, 0);
        bundle.setTronAddress(tronBase58Address(tronKey));
        bundle.setTronPrivateKey(tronKey.getPrivKey().toString(16));

        return bundle;
    }

    /* ------------------ 助记词相关 ------------------ */

    /**
     * 生成 12 词且互不相同的英文 BIP-39 助记词（128 位熵 + 校验），若有重复则重试
     */
    static List<String> generateUniqueMnemonic12() throws Exception {
        SecureRandom sr;
        try {
            sr = SecureRandom.getInstanceStrong();
        } catch (Exception e) {
            sr = new SecureRandom();
        }
        while (true) {
            byte[] entropy = new byte[16]; // 128 bits -> 12 words
            sr.nextBytes(entropy);
            List<String> words = MnemonicCode.INSTANCE.toMnemonic(entropy); // 含校验
            if (allDistinct(words)) return words; // 12 词全不同
        }
    }

    static boolean allDistinct(List<String> words) {
        return new HashSet<>(words).size() == words.size();
    }

    /**
     * 助记词(+passphrase) -> BIP32 主私钥
     */
    static DeterministicKey masterFromMnemonic(List<String> mnemonic, String passphrase) throws Exception {
        // 使用助记词生成种子（符合 BIP39）
        long creationTimeSeconds = System.currentTimeMillis() / 1000L;
        DeterministicSeed seed = new DeterministicSeed(mnemonic, null, passphrase, creationTimeSeconds);

        // 注意：真正的 master key 来源于 HDKeyDerivation，而不是 DeterministicKey 直接 fromSeed
        byte[] seedBytes = seed.getSeedBytes();
        if (seedBytes == null) {
            throw new IllegalStateException("Seed bytes are null, mnemonic may be invalid");
        }

        // 用 seed 派生出主 key（m/）
        return HDKeyDerivation.createMasterPrivateKey(seedBytes);
    }

    /* ------------------ BIP44 派生 ------------------ */

    /**
     * BIP44: m / 44' / coin_type' / account' / change / index
     * coin_type: BTC=0, ETH=60, TRON=195 (SLIP-44)
     */
    static DeterministicKey deriveBip44(DeterministicKey master, int coinType,
                                        int account, int change, int index) {
        DeterministicHierarchy dh = new DeterministicHierarchy(master);
        List<ChildNumber> path = Arrays.asList(
                new ChildNumber(44, true),
                new ChildNumber(coinType, true),
                new ChildNumber(account, true),
                new ChildNumber(change, false),
                new ChildNumber(index, false)
        );
        return dh.get(path, true, true);
    }

    /* ------------------ BTC 地址 (P2PKH) ------------------ */

    static String btcP2pkhAddress(DeterministicKey key) {
        NetworkParameters params = MainNetParams.get();
        // bitcoinj 0.15：用 LegacyAddress 生成 1xxx 地址（P2PKH）
        return LegacyAddress.fromKey(params, key).toString();
    }

    /* ------------------ ETH 地址 (EIP-55 校验) ------------------ */

    static String ethChecksumAddress(DeterministicKey key) {
        byte[] pubUncompressed = key.getPubKeyPoint().getEncoded(false); // 65B, 0x04 + 64B
        byte[] pubNoPrefix = Arrays.copyOfRange(pubUncompressed, 1, 65);
        // keccak256(pub[1..65])，取后 20 字节
        Keccak.Digest256 keccak = new Keccak.Digest256();
        byte[] hash = keccak.digest(pubNoPrefix);
        byte[] addr20 = Arrays.copyOfRange(hash, 12, 32);
        return toEip55(addr20);
    }

    static String toEip55(byte[] addr20) {
        String hex = bytesToHex(addr20).toLowerCase(Locale.ROOT); // 40 hex chars
        Keccak.Digest256 keccak = new Keccak.Digest256();
        byte[] digest = keccak.digest(hex.getBytes(StandardCharsets.US_ASCII));
        String hashHex = bytesToHex(digest);
        StringBuilder sb = new StringBuilder("0x");
        for (int i = 0; i < hex.length(); i++) {
            char c = hex.charAt(i);
            if (c >= 'a' && c <= 'f') {
                int v = Character.digit(hashHex.charAt(i), 16);
                sb.append(v > 7 ? Character.toUpperCase(c) : c);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /* ------------------ TRON 地址 (Base58Check, 以 T 开头) ------------------ */

    static String tronBase58Address(DeterministicKey key) {
        byte[] pubUncompressed = key.getPubKeyPoint().getEncoded(false);
        byte[] pubNoPrefix = Arrays.copyOfRange(pubUncompressed, 1, 65);
        Keccak.Digest256 keccak = new Keccak.Digest256();
        byte[] hash = keccak.digest(pubNoPrefix);
        byte[] payload20 = Arrays.copyOfRange(hash, 12, 32); // 后 20 字节
        // 版本前缀 0x41（主网），Base58Check
        return Base58.encodeChecked(0x41, payload20);
    }

    /* ------------------ 小工具 ------------------ */
    static String bytesToHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

}
