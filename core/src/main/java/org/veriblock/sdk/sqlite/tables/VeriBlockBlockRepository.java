// VeriBlock Blockchain Project
// Copyright 2017-2018 VeriBlock, Inc
// Copyright 2018-2019 Xenios SEZC
// All rights reserved.
// https://www.veriblock.org
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.

package org.veriblock.sdk.sqlite.tables;

import org.veriblock.sdk.blockchain.store.StoredVeriBlockBlock;
import org.veriblock.sdk.models.Sha256Hash;
import org.veriblock.sdk.models.VBlakeHash;
import org.veriblock.sdk.models.VeriBlockBlock;
import org.veriblock.sdk.services.SerializeDeserializeService;
import org.veriblock.sdk.util.Utils;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

public class VeriBlockBlockRepository extends GenericBlockRepository<StoredVeriBlockBlock, VBlakeHash> {

    private final static BlockSQLSerializer<StoredVeriBlockBlock, VBlakeHash> serializer =
                     new BlockSQLSerializer<StoredVeriBlockBlock, VBlakeHash>() {

        public void toStmt(StoredVeriBlockBlock block, PreparedStatement stmt) throws SQLException {
            int i = 0;
            stmt.setObject(++i, Utils.encodeHex(block.getHash().getBytes()));
            stmt.setObject(++i, Utils.encodeHex(block.getBlock().getPreviousBlock().getBytes()));
            stmt.setObject(++i, block.getHeight());
            stmt.setObject(++i, block.getWork().toString());
            stmt.setObject(++i, Utils.encodeHex(block.getBlockOfProof().getBytes()));
            stmt.setObject(++i, Utils.encodeHex(SerializeDeserializeService.serialize(block.getBlock())));
        }

        public StoredVeriBlockBlock fromResult(ResultSet result) throws SQLException {
            byte[] data = Utils.decodeHex(result.getString("data"));
            BigInteger work = new BigInteger(result.getString("work"));
            Sha256Hash blockOfProof = Sha256Hash.wrap(Utils.decodeHex(result.getString("blockOfProof")));

            VeriBlockBlock block = SerializeDeserializeService.parseVeriBlockBlock(ByteBuffer.wrap(data));
            StoredVeriBlockBlock storedBlock = new StoredVeriBlockBlock(block, work, blockOfProof);
            return storedBlock;
        }

        public String getSchema() {
            return  " id TEXT PRIMARY KEY,"
                  + " previousId TEXT,"
                  + " height INTEGER,"
                  + " work TEXT,"
                  + " blockOfProof TEXT,"
                  + " data TEXT";
        }

        public List<String> getColumns() {
            return Arrays.asList("id", "previousId", "height", "work", "blockOfProof", "data");
        }

        public String idToString(VBlakeHash hash) {
            return Utils.encodeHex(hash.getBytes());
        }
    };

    public VeriBlockBlockRepository(Connection connection) throws SQLException {
        super(connection, "veriBlockBlocks", serializer);
    }
}
