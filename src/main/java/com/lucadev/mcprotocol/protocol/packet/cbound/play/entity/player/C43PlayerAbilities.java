package com.lucadev.mcprotocol.protocol.packet.cbound.play.entity.player;

import com.lucadev.mcprotocol.Bot;
import com.lucadev.mcprotocol.game.PlayerAbilities;
import com.lucadev.mcprotocol.protocol.packet.AbstractPacket;
import com.lucadev.mcprotocol.protocol.packet.ReadablePacket;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * @author Luca Camphuisen < Luca.Camphuisen@hva.nl >
 */
public class C43PlayerAbilities extends AbstractPacket implements ReadablePacket {

    private PlayerAbilities playerAbilities;

    @Override
    public int getId() {
        return 0x2B;
    }

    /**
     * Read the data from the packet in here. This does not include packet id and stuff.
     *
     * @param is
     * @param totalSize total size of the data we're able to read.
     * @throws IOException
     */
    @Override
    public void read(Bot bot, DataInputStream is, int totalSize) throws IOException {
        byte b = is.readByte();
        float flySpeed = is.readFloat();
        float fovModifier = is.readFloat();
        playerAbilities = new PlayerAbilities(b, flySpeed, fovModifier);
    }

    public PlayerAbilities getPlayerAbilities() {
        return playerAbilities;
    }
}
