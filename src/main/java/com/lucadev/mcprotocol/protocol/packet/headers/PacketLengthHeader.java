package com.lucadev.mcprotocol.protocol.packet.headers;

import java.io.DataOutputStream;
import java.io.IOException;

import static com.lucadev.mcprotocol.protocol.VarHelper.writeVarInt;

/**
 * @author Luca Camphuisen < Luca.Camphuisen@hva.nl >
 */
public class PacketLengthHeader extends PacketHeader {
    private int length;

    public PacketLengthHeader(int id, int length) {
        super(id);
        this.length = length;
    }

    public int getLength() {
        return length;
    }

    @Override
    public void write(DataOutputStream os) throws IOException {
        writeVarInt(os, length);
        os.writeByte(getId());
    }

    @Override
    public String toString() {
        return "PacketLengthHeader{" +
                "id=0x" + Integer.toHexString(getId()).toUpperCase() + "," +
                "length=" + length +
                '}';
    }
}
