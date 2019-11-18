// VeriBlock Blockchain Project
// Copyright 2017-2018 VeriBlock, Inc
// Copyright 2018-2019 Xenios SEZC
// All rights reserved.
// https://www.veriblock.org
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.

package org.veriblock.protoservice;

import com.google.protobuf.ByteString;
import integration.api.grpc.VeriBlockMessages;
import integration.api.grpc.VeriBlockMessages.GeneralReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.veriblock.integrations.Context;
import org.veriblock.integrations.VeriBlockSecurity;
import org.veriblock.integrations.sqlite.tables.PoPTransactionData;
import org.veriblock.protoconverters.AltChainBlockProtoConverter;
import org.veriblock.protoconverters.AltChainParametresConfigProtoConverter;
import org.veriblock.protoconverters.AltPublicationProtoConverter;
import org.veriblock.protoconverters.BitcoinBlockProtoConverter;
import org.veriblock.protoconverters.BlockIndexProtoConverter;
import org.veriblock.protoconverters.PoPTransactionDataProtoConverter;
import org.veriblock.protoconverters.Sha256HashProtoConverter;
import org.veriblock.protoconverters.VBlakeHashProtoConverter;
import org.veriblock.protoconverters.VeriBlockBlockProtoConverter;
import org.veriblock.protoconverters.VeriBlockPublicationProtoConverter;
import org.veriblock.sdk.AltChainBlock;
import org.veriblock.sdk.AltPublication;
import org.veriblock.sdk.BlockIndex;
import org.veriblock.sdk.BlockStoreException;
import org.veriblock.sdk.Sha256Hash;
import org.veriblock.sdk.VBlakeHash;
import org.veriblock.sdk.ValidationResult;
import org.veriblock.sdk.VeriBlockPublication;
import org.veriblock.sdk.VerificationException;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class VeriBlockSecurityProtoService {
    private static final Logger log = LoggerFactory.getLogger(VeriBlockSecurityProtoService.class);
    private static VeriBlockSecurity security = null;
    
    private VeriBlockSecurityProtoService() { }
    
    public static void setVeriBlockSecurity(VeriBlockSecurity security) {
        VeriBlockSecurityProtoService.security = security;
    }
    
    public static GeneralReply resetSecurity() {
        ValidationResult result = null;
        try {
            Context.resetSecurity();
            result = ValidationResult.success();
        } catch (SQLException e) {
            result = ValidationResult.fail(e.getMessage());
            log.debug("Could not call VeriBlock security", e);
        }
        return VeriBlockServiceCommon.validationResultToProto(result);
    }
    
    public static GeneralReply addGenesisVeriBlock(VeriBlockMessages.VeriBlockBlock block) {
        ValidationResult result = null;
        try {
            ///TODO: make proper security method
            security.getVeriBlockBlockchain().add(VeriBlockBlockProtoConverter.fromProto(block));
            result = ValidationResult.success();
        } catch (BlockStoreException | VerificationException | SQLException e) {
            result = ValidationResult.fail(e.getMessage());
            log.debug("Could not call VeriBlock security", e);
        }
        return VeriBlockServiceCommon.validationResultToProto(result);
    }
    
    public static GeneralReply addGenesisBitcoin(VeriBlockMessages.BitcoinBlock block) {
        ValidationResult result = null;
        try {
            ///TODO: make proper security method
            security.getBitcoinBlockchain().add(BitcoinBlockProtoConverter.fromProto(block));
            result = ValidationResult.success();
        } catch (BlockStoreException | VerificationException | SQLException e) {
            result = ValidationResult.fail(e.getMessage());
            log.debug("Could not call VeriBlock security", e);
        }
        return VeriBlockServiceCommon.validationResultToProto(result);
    }
    
    public static GeneralReply addPayloads(VeriBlockMessages.BlockIndex blockIndexProto,
            List<VeriBlockMessages.AltPublication> altPublicationsProto,
            List<VeriBlockMessages.VeriBlockPublication> vtbPublicationsProto) {
        
        BlockIndex blockIndex = BlockIndexProtoConverter.fromProto(blockIndexProto);
        List<AltPublication> altPublications = AltPublicationProtoConverter.fromProto(altPublicationsProto);
        List<VeriBlockPublication> vtbPublications = VeriBlockPublicationProtoConverter.fromProto(vtbPublicationsProto);

        ValidationResult result = null;
        try {
            security.addPayloads(blockIndex, vtbPublications, altPublications);
            result = ValidationResult.success();
        } catch (VerificationException e) {
            result = ValidationResult.fail(e.getMessage());
        } catch (BlockStoreException | SQLException e) {
            result = ValidationResult.fail(e.getMessage());
            log.debug("Could not call VeriBlock security", e);
        }
        
        return VeriBlockServiceCommon.validationResultToProto(result);
    }
    
    public static GeneralReply removePayloads(VeriBlockMessages.BlockIndex blockIndexProto) {
        BlockIndex blockIndex = BlockIndexProtoConverter.fromProto(blockIndexProto);
        
        ValidationResult result = null;
        try {
            security.removePayloads(blockIndex);
            result = ValidationResult.success();
        } catch (BlockStoreException | SQLException e) {
            result = ValidationResult.fail(e.getMessage());
            log.debug("Could not call VeriBlock security", e);
        }
        
        return VeriBlockServiceCommon.validationResultToProto(result);
    }
    
    public static GeneralReply addTemporaryPayloads(List<VeriBlockMessages.AltPublication> altPublicationsProto,
            List<VeriBlockMessages.VeriBlockPublication> vtbPublicationsProto) {
        List<AltPublication> altPublications = AltPublicationProtoConverter.fromProto(altPublicationsProto);
        List<VeriBlockPublication> vtbPublications = VeriBlockPublicationProtoConverter.fromProto(vtbPublicationsProto);
        
        ValidationResult result = null;
        try {
            security.addTemporaryPayloads(vtbPublications, altPublications);
            result = ValidationResult.success();
        } catch (VerificationException e) {
            result = ValidationResult.fail(e.getMessage());
        } catch (BlockStoreException | SQLException e) {
            result = ValidationResult.fail(e.getMessage());
            log.debug("Could not call VeriBlock security", e);
        }
        
        return VeriBlockServiceCommon.validationResultToProto(result);
    }
    
    public static GeneralReply clearTemporaryPayloads() {
        ValidationResult result = null;
        security.clearTemporaryPayloads();
        result = ValidationResult.success();
        return VeriBlockServiceCommon.validationResultToProto(result);
    }
    
    public static VeriBlockMessages.SimplifyVTBsReply simplifyVTBs(List<VeriBlockMessages.VeriBlockPublication> vtbPublicationsProto) {
        List<VeriBlockPublication> vtbPublications = VeriBlockPublicationProtoConverter.fromProto(vtbPublicationsProto);
        
        ValidationResult result = null;
        List<VeriBlockPublication> simplifiedPublications = new ArrayList<>();
        try {
            simplifiedPublications = security.simplifyVTBs(vtbPublications);
            result = ValidationResult.success();
        } catch (BlockStoreException | SQLException e) {
            result = ValidationResult.fail(e.getMessage());
            log.debug("Could not call VeriBlock security", e);
        }

        GeneralReply replyResult = VeriBlockServiceCommon.validationResultToProto(result);
        
        List<VeriBlockMessages.VeriBlockPublication> protoData = VeriBlockPublicationProtoConverter.toProto(simplifiedPublications);
        VeriBlockMessages.SimplifyVTBsReply reply = VeriBlockMessages.SimplifyVTBsReply.newBuilder()
                .addAllVeriblockPublications(protoData)
                .setResult(replyResult)
                .build();
        return reply;
    }
    
    public static GeneralReply checkATVAgainstView(VeriBlockMessages.AltPublication publication) {        
        ValidationResult result = null;
        try {
            result = security.checkATVAgainstView(AltPublicationProtoConverter.fromProto(publication));
        } catch (BlockStoreException | SQLException e) {
            result = ValidationResult.fail(e.getMessage());
            log.debug("Could not call VeriBlock security", e);
        }

        return VeriBlockServiceCommon.validationResultToProto(result);
    }
    
    public static GeneralReply checkVTBInternally(VeriBlockMessages.VeriBlockPublication publication) {
        ValidationResult result = null;
        try {
            result = security.checkVTBInternally(VeriBlockPublicationProtoConverter.fromProto(publication));
        } catch (BlockStoreException e) {
            result = ValidationResult.fail(e.getMessage());
            log.debug("Could not call VeriBlock security", e);
        }
        return VeriBlockServiceCommon.validationResultToProto(result);
    }

    public static GeneralReply checkATVInternally(VeriBlockMessages.AltPublication publication) {
        ValidationResult result = null;
        try {
            result = security.checkATVInternally(AltPublicationProtoConverter.fromProto(publication));
        } catch (BlockStoreException e) {
            result = ValidationResult.fail(e.getMessage());
            log.debug("Could not call VeriBlock security", e);
        }
        return VeriBlockServiceCommon.validationResultToProto(result);
    }
    
    public static VeriBlockMessages.GetMainVBKHeightOfATVReply getMainVBKHeightOfATV(VeriBlockMessages.AltPublication publication) {        
        ValidationResult result = null;
        int height = 0;
        try {
            height = security.getMainVBKHeightOfATV(AltPublicationProtoConverter.fromProto(publication));
            result = ValidationResult.success();
        } catch (BlockStoreException | SQLException e) {
            result = ValidationResult.fail(e.getMessage());
            log.debug("Could not call VeriBlock security", e);
        }
        
        GeneralReply replyResult = VeriBlockServiceCommon.validationResultToProto(result);
        VeriBlockMessages.GetMainVBKHeightOfATVReply reply = VeriBlockMessages.GetMainVBKHeightOfATVReply.newBuilder()
                .setHeight(height)
                .setResult(replyResult)
                .build();
        return reply;
    }

    public static VeriBlockMessages.GeneralReply setAltChainParametersConfig(VeriBlockMessages.AltChainConfigRequest config) {
        security.setAltChainParametersConfig(AltChainParametresConfigProtoConverter.fromProto(config));
        return VeriBlockServiceCommon.validationResultToProto(ValidationResult.success());
    }

    public static VeriBlockMessages.GeneralReply savePoPTransactionData(VeriBlockMessages.SavePoPTransactionDataRequest request) {
        ValidationResult result = null;

        try {
            PoPTransactionData popTx = PoPTransactionDataProtoConverter.fromProto(request.getPopTx());
            AltChainBlock containingBlock = AltChainBlockProtoConverter.fromProto(request.getContainingBlock());
            AltChainBlock endorsedBlock = AltChainBlockProtoConverter.fromProto(request.getEndorsedBlock());
            Context.getPopTxDBStore().addPoPTransaction(popTx, containingBlock, endorsedBlock);
            result = ValidationResult.success();
        }
        catch (SQLException e) {
            result = ValidationResult.fail(e.getMessage());
            log.debug("Could not call VeriBlock security", e);
        }

        return VeriBlockServiceCommon.validationResultToProto(result);
    }

    public static VeriBlockMessages.GetLastKnownVBKBlocksReply getLastKnownVBKBlocks(VeriBlockMessages.GetLastKnownBlocksRequest request) {
        ValidationResult result = null;
        List<VBlakeHash> blocks = new ArrayList<>();
        try {
            blocks = security.getLastKnownVBKBlocks(request.getMaxBlockCount());
            result = ValidationResult.success();
        } catch (BlockStoreException | SQLException e) {
            result = ValidationResult.fail(e.getMessage());
            log.debug("Could not call VeriBlock security", e);
        }

        GeneralReply replyResult = VeriBlockServiceCommon.validationResultToProto(result);
        List<ByteString> protoBlocks = VBlakeHashProtoConverter.toProto(blocks);

        VeriBlockMessages.GetLastKnownVBKBlocksReply reply = VeriBlockMessages.GetLastKnownVBKBlocksReply.newBuilder()
                .addAllBlocks(protoBlocks)
                .setResult(replyResult)
                .build();
        return reply;
    }

    public static VeriBlockMessages.GetLastKnownBTCBlocksReply getLastKnownBTCBlocks(VeriBlockMessages.GetLastKnownBlocksRequest request) {
        ValidationResult result = null;
        List<Sha256Hash> blocks = new ArrayList<>();
        try {
            blocks = security.getLastKnownBTCBlocks(request.getMaxBlockCount());
            result = ValidationResult.success();
        } catch (BlockStoreException | SQLException e) {
            result = ValidationResult.fail(e.getMessage());
            log.debug("Could not call VeriBlock security", e);
        }

        GeneralReply replyResult = VeriBlockServiceCommon.validationResultToProto(result);
        List<ByteString> protoBlocks = Sha256HashProtoConverter.toProto(blocks);

        VeriBlockMessages.GetLastKnownBTCBlocksReply reply = VeriBlockMessages.GetLastKnownBTCBlocksReply.newBuilder()
                .addAllBlocks(protoBlocks)
                .setResult(replyResult)
                .build();
        return reply;
    }
}
