package com.jinny.crypto.modules.crypto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 
 * </p>
 *
 * @author admin
 * @since 2025-09-10
 */
@Getter
@Setter
@TableName("crypto_info")
public class CryptoInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String mnemonic;

    private String btcAddress;

    private String btcPrivateKey;

    private String ethAddress;

    private String ethPrivateKey;

    private String ethAmount;

    private String tronAddress;

    private String tronPrivateKey;

    private String tronAmount;


}
