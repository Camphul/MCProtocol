package com.lucadev.mcprotocol.game.tick;

import com.lucadev.mcprotocol.Bot;

/**
 * @author Luca Camphuisen < Luca.Camphuisen@hva.nl >
 */
public interface TickListener {

    void onAction(Bot bot);
}
