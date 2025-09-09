package com.jinny.crypto.common.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WalletBundle {

    private List<String> mnemonic;  // 助记词
    private String btcAddress;
    private String btcPrivateKey;   // WIF 格式

    private String ethAddress;
    private String ethPrivateKey;   // hex 格式

    private String tronAddress;
    private String tronPrivateKey;  // hex 格式

}
