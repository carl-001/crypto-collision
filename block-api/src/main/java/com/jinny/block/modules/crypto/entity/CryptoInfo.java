package com.jinny.crypto.modules.crypto.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
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
@ApiModel(value = "CryptoInfo对象", description = "")
public class CryptoInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("主键")
    private Integer id;

    @ApiModelProperty("助记词")
    private String mnemonic;

    private String btcAddress;

    private String btcPrivateKey;

    private String ethAddress;

    private String ethPrivateKey;

    private String tronAddress;

    private String tronPrivateKey;


}
