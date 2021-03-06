// VeriBlock Blockchain Project
// Copyright 2017-2018 VeriBlock, Inc
// Copyright 2018-2019 Xenios SEZC
// All rights reserved.
// https://www.veriblock.org
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.

package org.veriblock.sdk.mock;

import org.veriblock.sdk.blockchain.store.BlockStore;
import org.veriblock.sdk.blockchain.store.StoredBitcoinBlock;
import org.veriblock.sdk.conf.BitcoinNetworkParameters;
import org.veriblock.sdk.models.Sha256Hash;
import org.veriblock.sdk.models.BitcoinBlock;
import org.veriblock.sdk.models.VerificationException;
import org.veriblock.sdk.services.ValidationService;
import org.veriblock.sdk.util.Utils;

import java.sql.SQLException;
import java.util.Map;
import java.util.HashMap;

public class BitcoinBlockchain extends org.veriblock.sdk.blockchain.BitcoinBlockchain {
    private final Map<Sha256Hash, BitcoinBlockData> blockDataStore = new HashMap<>();

    public BitcoinBlockchain(BitcoinNetworkParameters networkParameters,
                      BlockStore<StoredBitcoinBlock, Sha256Hash> store) {
        super(networkParameters, store);
    }

    public BitcoinBlock mine(BitcoinBlockData blockData) throws SQLException {
        BitcoinBlock chainHead = getChainHead();

        int timestamp = 0;

        for (int nonce = 0; nonce < Integer.MAX_VALUE; nonce++) {
            try {
                timestamp = Math.max(timestamp, Utils.getCurrentTimestamp());

                BitcoinBlock newBlock = new BitcoinBlock(
                    chainHead.getVersion(),
                    chainHead.getHash(),
                    blockData.getMerkleRoot().getReversed(),
                    timestamp,
                    // FIXME: remove the hardcoded regtest difficulty adjustment
                    // use the difficulty calculator to set the correct difficulty
                    chainHead.getBits(),
                    nonce);
                                                            
                ValidationService.checkProofOfWork(newBlock);

                add(newBlock);
                blockDataStore.put(newBlock.getHash(), blockData);

                return newBlock; 
            } catch (VerificationException e) {
                // FIXME: refactoring checkTimestamp() would make this less ugly
                // if too many blocks are mined per second, we have to adjust the timestamp slightly
                if (e.getMessage().equals("Block is too far in the past")) {
                    timestamp++;

                // FIXME: refactoring checkProofOfWork() would make this less ugly
                // suppress this specific exception as it's just a signal
                // that the block hash does not match the block difficulty
                } else if (!e.getMessage().startsWith("Block hash is higher than target")) {
                    throw e;
                }
            }
        }
        throw new RuntimeException("Failed to mine the block due to too high difficulty");
    }
}
