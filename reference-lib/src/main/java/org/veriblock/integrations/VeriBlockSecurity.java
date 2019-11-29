// VeriBlock Blockchain Project
// Copyright 2017-2018 VeriBlock, Inc
// Copyright 2018-2019 Xenios SEZC
// All rights reserved.
// https://www.veriblock.org
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.

package org.veriblock.integrations;

import org.veriblock.integrations.auditor.AuditJournal;
import org.veriblock.integrations.auditor.BlockIdentifier;
import org.veriblock.integrations.auditor.Change;
import org.veriblock.integrations.auditor.Changeset;
import org.veriblock.integrations.blockchain.BitcoinBlockchain;
import org.veriblock.integrations.blockchain.VeriBlockBlockchain;
import org.veriblock.integrations.blockchain.VeriBlockPublicationUtilities;
import org.veriblock.integrations.blockchain.store.BitcoinStore;
import org.veriblock.sdk.AltPublication;
import org.veriblock.sdk.BitcoinBlock;
import org.veriblock.sdk.BlockIndex;
import org.veriblock.sdk.BlockStoreException;
import org.veriblock.sdk.Sha256Hash;
import org.veriblock.sdk.VBlakeHash;
import org.veriblock.sdk.ValidationResult;
import org.veriblock.sdk.VeriBlockBlock;
import org.veriblock.sdk.VeriBlockPublication;
import org.veriblock.sdk.VerificationException;
import org.veriblock.sdk.services.ValidationService;
import org.veriblock.sdk.util.Utils;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class VeriBlockSecurity {

    private final VeriBlockBlockchain veriblockBlockchain;
    private final BitcoinBlockchain bitcoinBlockchain;
    private final AuditJournal journal;
    private final BitcoinStore bitcoinStore;
    private AltChainParametersConfig altChainParametersConfig;

    public VeriBlockSecurity() {
        veriblockBlockchain = new VeriBlockBlockchain(Context.getVeriBlockNetworkParameters(), Context.getVeriblockStore(), Context.getBitcoinStore());
        bitcoinBlockchain = new BitcoinBlockchain(Context.getBitcoinNetworkParameters(), Context.getBitcoinStore());
        journal = new AuditJournal(Context.getChangeStore());
        bitcoinStore = Context.getBitcoinStore();
        altChainParametersConfig = new AltChainParametersConfig();
    }
    
    public void shutdown() {
        Context.getBitcoinStore().shutdown();
        Context.getVeriblockStore().shutdown();
        Context.getChangeStore().shutdown();
        Context.getPopTxDBStore().shutdown();
    }
    
    public VeriBlockBlockchain getVeriBlockBlockchain() {
        return veriblockBlockchain;
    }
    
    public BitcoinBlockchain getBitcoinBlockchain() {
        return bitcoinBlockchain;
    }
    
    public void setAltChainParametersConfig(AltChainParametersConfig config) { this.altChainParametersConfig = config; }

    public AltChainParametersConfig getAltChainParametersConfig() { return this.altChainParametersConfig; }

    public ValidationResult checkATVInternally(AltPublication publication) {
        try {
            ValidationService.verify(publication);

            return ValidationResult.success();
        } catch (VerificationException e) {
            return ValidationResult.fail(e.getMessage());
        }
    }

    public ValidationResult checkVTBInternally(VeriBlockPublication publication) {
        try {
            ValidationService.verify(publication);

            return ValidationResult.success();
        } catch (VerificationException e) {
            return ValidationResult.fail(e.getMessage());
        }
    }

    // TODO: Exception when blockIndex.height is less than or equal to highest known
    public void addPayloads(BlockIndex blockIndex, List<VeriBlockPublication> veriblockPublications, List<AltPublication> altPublications) throws VerificationException, BlockStoreException, SQLException {
        Changeset changeset = new Changeset(BlockIdentifier.wrap(Utils.decodeHex(blockIndex.getHash())));

        try {
            if (veriblockPublications != null && veriblockPublications.size() > 0) {
                for (VeriBlockPublication publication : veriblockPublications) {
                    ValidationService.verify(publication);
                    verifyPublicationContextually(publication);

                    changeset.addChanges(bitcoinBlockchain.addAll(publication.getTransaction().getBlocks()));

                    List<VeriBlockBlock> veriBlockBlocks = publication.getBlocks();
                    if (veriBlockBlocks.contains(publication.getTransaction().getPublishedBlock())) {
                        // The published block is part of this publication's supplied context, add the blocks individually
                        for (VeriBlockBlock block : veriBlockBlocks) {
                            if (block.equals(publication.getTransaction().getPublishedBlock())) {
                                changeset.addChanges(veriblockBlockchain.addWithProof(block, publication.getTransaction().getBlockOfProof().getHash()));
                            } else {
                                changeset.addChanges(veriblockBlockchain.add(block));
                            }
                        }
                    } else {
                        // The published block is pre-existing, therefore set its block of proof and add these new blocks
                        changeset.addChanges(veriblockBlockchain.setBlockOfProof(
                                publication.getTransaction().getPublishedBlock(),
                                publication.getTransaction().getBlockOfProof().getHash()));
                        changeset.addChanges(veriblockBlockchain.addAll(publication.getBlocks()));
                    }
                }
            }

            if (altPublications != null && altPublications.size() > 0) {
                for (AltPublication publication : altPublications) {
                    ValidationService.verify(publication);
                    verifyPublicationContextually(publication);

                    changeset.addChanges(veriblockBlockchain.addAll(publication.getBlocks()));
                }
            }

            journal.record(changeset);

        } catch (VerificationException e) {
            Iterator<Change> changeIterator = changeset.reverseIterator();
            while (changeIterator.hasNext()) {
                Change change = changeIterator.next();
                bitcoinBlockchain.rewind(Collections.singletonList(change));
                veriblockBlockchain.rewind(Collections.singletonList(change));
            }
            throw e;
        }
    }

    public void removePayloads(BlockIndex blockIndex) throws SQLException {
        BlockIdentifier blockIdentifier = BlockIdentifier.wrap(Utils.decodeHex(blockIndex.getHash()));

        List<Change> changes = journal.get(blockIdentifier);
        veriblockBlockchain.rewind(changes);
        bitcoinBlockchain.rewind(changes);
    }

    public void addTemporaryPayloads(List<VeriBlockPublication> veriblockPublications, List<AltPublication> altPublications) throws VerificationException, BlockStoreException, SQLException {
        try {
            if (veriblockPublications != null && veriblockPublications.size() > 0) {
                for (VeriBlockPublication publication : veriblockPublications) {
                    ValidationService.verify(publication);
                    verifyPublicationContextually(publication);

                    // Temporarily add Bitcoin blocks
                    bitcoinBlockchain.addAllTemporarily(publication.getTransaction().getBlocks());

                    List<VeriBlockBlock> veriBlockBlocks = publication.getBlocks();
                    if (veriBlockBlocks.contains(publication.getTransaction().getPublishedBlock())) {
                        // The published block is part of this publication's supplied context, add the blocks individually
                        for (VeriBlockBlock block : veriBlockBlocks) {
                            if (block.equals(publication.getTransaction().getPublishedBlock())) {
                                veriblockBlockchain.addTemporarily(block, publication.getTransaction().getBlockOfProof().getHash());
                            } else {
                                veriblockBlockchain.addTemporarily(block);
                            }
                        }
                    } else {
                        // The published block is pre-existing, therefore set its block of proof and add these new blocks
                        veriblockBlockchain.setBlockOfProofTemporarily(
                                publication.getTransaction().getPublishedBlock(),
                                publication.getTransaction().getBlockOfProof().getHash());
                        veriblockBlockchain.addAllTemporarily(publication.getBlocks());
                    }
                }
            }

            if (altPublications != null && altPublications.size() > 0) {
                for (AltPublication publication : altPublications) {
                    ValidationService.verify(publication);
                    verifyPublicationContextually(publication);

                    veriblockBlockchain.addAllTemporarily(publication.getBlocks());
                }
            }

        } catch (VerificationException e) {
            clearTemporaryPayloads();
            throw e;
        }
    }

    public void clearTemporaryPayloads() {
        veriblockBlockchain.clearTemporaryModifications();
        bitcoinBlockchain.clearTemporaryModifications();
    }

    public List<VeriBlockPublication> simplifyVTBs(List<VeriBlockPublication> publications) throws BlockStoreException, SQLException {
        return VeriBlockPublicationUtilities.simplifyVeriBlockPublications(publications, bitcoinStore);
    }

    public ValidationResult checkATVAgainstView(AltPublication publication) throws BlockStoreException, SQLException {
        try {
            ValidationService.verify(publication);
            verifyPublicationContextually(publication);

            return ValidationResult.success();
        } catch (VerificationException e) {
            return ValidationResult.fail(e.getMessage());
        }
    }

    public int getMainVBKHeightOfATV(AltPublication publication) throws BlockStoreException, SQLException {
        VeriBlockBlock block = veriblockBlockchain.searchBestChain(publication.getContainingBlock().getHash());
        return block != null ? block.getHeight() : Integer.MAX_VALUE;
    }

    public List<VBlakeHash> getLastKnownVBKBlocks(int maxBlockCount) throws SQLException {
        List<VBlakeHash> result = new ArrayList<>(maxBlockCount);

        VeriBlockBlock block = veriblockBlockchain.getChainHead();
        for (int count = 0; block != null && count < maxBlockCount; ++count) {
            result.add(block.getHash());

            VBlakeHash prevBlockHash = block.getPreviousBlock();
            block = prevBlockHash == null ? null
                                          : veriblockBlockchain.get(prevBlockHash);
        }

        return result;
    }

    public List<Sha256Hash> getLastKnownBTCBlocks(int maxBlockCount) throws SQLException {
        List<Sha256Hash> result = new ArrayList<>(maxBlockCount);

        BitcoinBlock block = bitcoinBlockchain.getChainHead();
        for (int count = 0; block != null && count < maxBlockCount; ++count) {
            result.add(block.getHash());

            Sha256Hash prevBlockHash = block.getPreviousBlock();
            block = prevBlockHash == null ? null
                                          : bitcoinBlockchain.get(prevBlockHash);
        }

        return result;
    }

    private void verifyPublicationContextually(VeriBlockPublication publication) throws VerificationException, BlockStoreException, SQLException {
        checkConnectivity(publication.getFirstBlock());
        checkConnectivity(publication.getFirstBitcoinBlock());
    }

    private void verifyPublicationContextually(AltPublication publication) throws VerificationException, BlockStoreException, SQLException {
        checkConnectivity(publication.getFirstBlock());
    }

    public void checkConnectivity(VeriBlockBlock block) throws BlockStoreException, SQLException {
        if (block == null) {
            throw new VerificationException("Publication does not have any VeriBlock blocks");
        }

        VeriBlockBlock previous = veriblockBlockchain.searchBestChain(block.getPreviousBlock());
        if (previous != null) {
            return;
        }

        // corner case: the first bootstrap block has no previous block
        // but does connect to the blockchain by definition
        if (veriblockBlockchain.searchBestChain(block.getHash()) == null) {
            throw new VerificationException("Publication does not connect to VeriBlock blockchain");
        }
    }

    public void checkConnectivity(BitcoinBlock block) throws BlockStoreException, SQLException {
        if (block == null) {
            throw new VerificationException("Publication does not have any Bitcoin blocks");
        }

        BitcoinBlock previous = bitcoinBlockchain.searchBestChain(block.getPreviousBlock());
        if (previous != null) {
            return;
        }

        // corner case: the first bootstrap block has no previous block
        // but does connect to the blockchain by definition
        if (bitcoinBlockchain.searchBestChain(block.getHash()) == null) {
            throw new VerificationException("Publication does not connect to Bitcoin blockchain");
        }
    }
}
