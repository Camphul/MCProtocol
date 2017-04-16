package com.lucadev.mcprotocol.protocol.impl.v316;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucadev.mcprotocol.Bot;
import com.lucadev.mcprotocol.game.chat.components.ChatComponent;
import com.lucadev.mcprotocol.game.entity.player.BotPlayer;
import com.lucadev.mcprotocol.game.tick.TickEngineFactory;
import com.lucadev.mcprotocol.game.world.World;
import com.lucadev.mcprotocol.protocol.AbstractProtocol;
import com.lucadev.mcprotocol.protocol.ProtocolException;
import com.lucadev.mcprotocol.protocol.State;
import com.lucadev.mcprotocol.protocol.network.client.NetClient;
import com.lucadev.mcprotocol.protocol.network.client.impl.DefaultNetClient;
import com.lucadev.mcprotocol.protocol.network.client.util.ReadTask;
import com.lucadev.mcprotocol.protocol.network.connection.Connection;
import com.lucadev.mcprotocol.protocol.network.connection.impl.KeySecuredConnection;
import com.lucadev.mcprotocol.protocol.packets.PacketListener;
import com.lucadev.mcprotocol.protocol.packets.ReadablePacket;
import com.lucadev.mcprotocol.protocol.packets.cbound.login.C00Disconnect;
import com.lucadev.mcprotocol.protocol.packets.cbound.login.C01EncryptionRequest;
import com.lucadev.mcprotocol.protocol.packets.cbound.login.C02LoginSuccess;
import com.lucadev.mcprotocol.protocol.packets.cbound.login.C03SetCompression;
import com.lucadev.mcprotocol.protocol.packets.cbound.play.*;
import com.lucadev.mcprotocol.protocol.packets.cbound.play.entity.*;
import com.lucadev.mcprotocol.protocol.packets.cbound.play.entity.mob.C03SpawnMob;
import com.lucadev.mcprotocol.protocol.packets.cbound.play.entity.player.*;
import com.lucadev.mcprotocol.protocol.packets.cbound.play.world.chunk.C09UpdateBlockEntity;
import com.lucadev.mcprotocol.protocol.packets.cbound.play.world.chunk.C32ChunkData;
import com.lucadev.mcprotocol.protocol.packets.headers.PacketLengthHeader;
import com.lucadev.mcprotocol.protocol.packets.sbound.handshake.S00Handshake;
import com.lucadev.mcprotocol.protocol.packets.sbound.login.S00LoginStart;
import com.lucadev.mcprotocol.protocol.packets.sbound.login.S01EncryptionResponse;
import com.lucadev.mcprotocol.protocol.packets.sbound.play.S02ChatMessage;
import com.lucadev.mcprotocol.protocol.packets.sbound.play.S10KeepAlive;
import com.lucadev.mcprotocol.protocol.packets.sbound.play.client.S03ClientStatus;
import com.lucadev.mcprotocol.protocol.packets.sbound.play.client.S04ClientSettings;
import com.lucadev.mcprotocol.protocol.packets.sbound.play.entity.player.S00TeleportConfirm;
import com.lucadev.mcprotocol.protocol.packets.sbound.play.entity.player.S13PlayerPositionLook;
import com.lucadev.mcprotocol.protocol.packets.sbound.status.S00Request;
import com.lucadev.mcprotocol.protocol.pluginchannel.PluginChannelManager;
import com.lucadev.mcprotocol.protocol.pluginchannel.PluginChannelManagerFactory;
import com.lucadev.mcprotocol.protocol.pluginchannel.channels.MCBrandPluginChannel;
import com.lucadev.mcprotocol.game.tick.TickEngine;
import com.lucadev.mcprotocol.util.EncryptionUtil;
import com.lucadev.mcprotocol.util.model.MOTDResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.lucadev.mcprotocol.protocol.VarHelper.readString;


/**
 * Implementation of protocol id 316
 *
 * @author Luca Camphuisen < Luca.Camphuisen@hva.nl >
 */
public class Protocol316 extends AbstractProtocol {

    /**
     * System logger
     */
    private static final Logger logger = LoggerFactory.getLogger(Protocol316.class);

    private Bot bot;
    private PluginChannelManager pluginChannelManager;
    private HashMap<Class<? extends ReadablePacket>, List<PacketListener>> listenerMap = new HashMap<>();
    private TickEngine tickEngine;

    /**
     * Setup the protocol.
     * @param bot instance of our bot
     */
    @Override
    public void setup(Bot bot) {
        this.bot = bot;
        tickEngine = TickEngineFactory.getDefaultFactory().createEngine(bot);
        pluginChannelManager = PluginChannelManagerFactory.getDefaultFactory().createPluginChannelManager();
        setupPluginChannels();
        setupPacketListeners();
        setupTickWorkers();
        register(State.LOGIN, 0x00, C00Disconnect.class);
        register(State.LOGIN, 0x01, C01EncryptionRequest.class);
        register(State.LOGIN, 0x02, C02LoginSuccess.class);
        register(State.LOGIN, 0x03, C03SetCompression.class);
        register(State.PLAY, 0x23, C35JoinGame.class);
        register(State.PLAY, 0x0D, C13ServerDifficulty.class);
        register(State.PLAY, 0x18, C24PluginMessage.class);
        register(State.PLAY, 0x43, C67SpawnPosition.class);
        register(State.PLAY, 0x2B, C43PlayerAbilities.class);
        register(State.PLAY, 0x2E, C46PlayerPositionLook.class);
        register(State.PLAY, 0x37, C55SlotChange.class);//WRITE HANDLER
        register(State.PLAY, 0x0F, C16ChatMessage.class);
        register(State.PLAY, 0x1F, C31KeepAlive.class);
        register(State.PLAY, 0x1A, C26Disconnect.class);
        register(State.PLAY, 0x2D, C45UpdatePlayerListItem.class);
        register(State.PLAY, 0x20, C32ChunkData.class);
        register(State.PLAY, 0x03, C03SpawnMob.class);
        register(State.PLAY, 0x39, C57UpdateEntityMetadata.class);
        register(State.PLAY, 0x4A, C74EntitityProperties.class);
        register(State.PLAY, 0x34, C52EntityHeadLook.class);
        register(State.PLAY, 0x3C, C60EntityEquipment.class);
        register(State.PLAY, 0x25, C37EntityRelativeMove.class);
        register(State.PLAY, 0x09, C09UpdateBlockEntity.class);
    }

    /**
     * Register all tick workers for the protocol in this method.
     */
    private void setupTickWorkers() {
        //Send player pos and look every 1 sec when not moving.
        getTickEngine().register(20, (bot) -> {
            try {
                bot.getNetClient().sendPacket(new S13PlayerPositionLook(bot.getPlayer()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        //tick method for protocol class itself.
        getTickEngine().register(1, (bot) -> {
            tick();
        });

    }

    /**
     * Register all the supported plugin channels in here.
     */
    private void setupPluginChannels() {
        pluginChannelManager.register(new MCBrandPluginChannel());
    }

    /**
     * Setup all packets listeners for this protocol.
     */
    private void setupPacketListeners() {
        registerPacketListener(C24PluginMessage.class, (bot, packet) -> {
            C24PluginMessage pluginMessage = (C24PluginMessage) packet;
            try {
                pluginChannelManager.handle(pluginMessage.getChannelName(), pluginMessage.getData());
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        registerPacketListener(C13ServerDifficulty.class, (bot, packet) -> {
            C13ServerDifficulty serverDifficulty = (C13ServerDifficulty) packet;
            bot.getPlayer().setDifficulty(serverDifficulty.getDifficulty());
        });

        registerPacketListener(C67SpawnPosition.class, (bot, packet) -> {
            C67SpawnPosition spawnPosition = (C67SpawnPosition) packet;
            //logger.info("Updated spawn positon to " + spawnPosition.getPosition().toString());
        });

        registerPacketListener(C43PlayerAbilities.class, (bot, packet) -> {
            C43PlayerAbilities playerAbilities = (C43PlayerAbilities) packet;
            //logger.info("Updated player abilities: {}", playerAbilities.getPlayerAbilities().toString());
            bot.getPlayer().setPlayerAbilities(playerAbilities.getPlayerAbilities());
        });

        registerPacketListener(C46PlayerPositionLook.class, (bot, packet) -> {
            C46PlayerPositionLook positionLook = (C46PlayerPositionLook) packet;
            bot.getPlayer().setPosition(positionLook.getX(), positionLook.getY(), positionLook.getZ());
            bot.getPlayer().setYawPitch(positionLook.getYaw(), positionLook.getPitch());
            //send tp confirmation
            //logger.info("Updating player pos from server");
            try {
                bot.getNetClient().sendPacket(new S00TeleportConfirm(positionLook.getTeleportId()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        registerPacketListener(C16ChatMessage.class, (bot, packet) -> {
            C16ChatMessage c16ChatMessage = (C16ChatMessage) packet;
            onChatMessage(c16ChatMessage.getChatComponent(), c16ChatMessage.getPosition());
        });

        registerPacketListener(C31KeepAlive.class, (bot, packet) -> {
            C31KeepAlive keepAlive = (C31KeepAlive) packet;
            //logger.info("KeepAlive received");
            try {
                bot.getNetClient().sendPacket(new S10KeepAlive(keepAlive.getKeepAliveId()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        registerPacketListener(C26Disconnect.class, (bot, packet) -> {
            logger.info("SERVER DISCONNECTED REASON: " + ((C26Disconnect) packet).getReason().getCompleteText());
            try {
                bot.getConnection().close();
                bot.getNetClient().close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Get the supported protocol protocolVersion of the protocol.
     * @return protocol id
     */
    @Override
    public int getProtocolID() {
        return 316;
    }

    /**
     * This method will handle all packets from the handshake state until the play state which means you've logged in.
     * @throws IOException when something goes wrong with the stream or login.
     */
    @Override
    public void serverLogin() throws IOException {
        String host = bot.getConnection().getSocket().getInetAddress().getCanonicalHostName();
        int port = bot.getConnection().getSocket().getPort();
        String username = bot.getSession().getProfileName();
        NetClient client = bot.getNetClient();
        client.writePacket(new S00Handshake(host, port, getProtocolID(), State.LOGIN));
        setCurrentState(State.LOGIN);
        client.writePacket(new S00LoginStart(username));
        ReadablePacket read = client.readPacket();
        if(checkLoginFailure(read)) {
            return;
        }
        if(read instanceof C01EncryptionRequest) {
            setupCrypto((C01EncryptionRequest)read);
            read = client.readPacket();
        }

        if(checkLoginFailure(read)) {
            return;
        }

        if (read instanceof C03SetCompression) {
            C03SetCompression comp = (C03SetCompression) read;
            enableCompression(comp.getThreshold());
            read = client.readPacket();
        }

        if (read instanceof C02LoginSuccess) {
            logger.info("Login success!!!");
            logger.info(read.toString());
        }

        setCurrentState(State.PLAY);
        ReadablePacket joingamePacket = client.readPacket();
        logger.info(joingamePacket.toString());
        if (joingamePacket instanceof C35JoinGame) {
            C35JoinGame joinGame = (C35JoinGame) joingamePacket;
            C02LoginSuccess loginSuccess = (C02LoginSuccess) read;
            bot.setPlayer(new BotPlayer(joinGame.getEntityId(), loginSuccess.getUuid(), loginSuccess.getUsername(), joinGame.getGameMode(), joinGame.getDimension(),
                    joinGame.getDifficulty(), joinGame.getLevelType(), joinGame.isReducedDebug()));
        }

        ReadablePacket packet = client.readPacket();
        while (!(packet instanceof C43PlayerAbilities)) {
            handlePacket(packet);
            packet = client.readPacket();
        }
        handlePacket(packet);
        pluginChannelManager.registerToServer(bot, client);

        client.writePacket(new S04ClientSettings());
        packet = client.readPacket();
        handlePacket(packet);
        client.writePacket(new S03ClientStatus(S03ClientStatus.ClientAction.PERFORM_RESPAWN));
        //start the tickengine
        bot.setWorld(new World());
        new ReadTask(bot).start();
        tickEngine.start(true);
    }

    /**
     * Checks if the packet is a disconnect packet sent. If it is it will disconnect us too and return.
     * @param packet
     */
    private boolean checkLoginFailure(ReadablePacket packet) throws IOException {
        if(packet instanceof C00Disconnect) {
            logger.info("Server sent disconnect while logging in. Reason given: " + ((C00Disconnect)packet).getTextReason());
            disconnect();
            return true;
        }
        return false;
    }

    /**
     * Obtain the server's MOTD and disconnects afterwards. Should only function if serverLogin has not been called/we aren't logged in.
     *
     * @return server MOTD response in a POJO
     * @throws IOException thrown if we fail to obtain the MOTD
     */
    @Override
    public MOTDResponse getMOTD() throws IOException {
        if (bot.isConnected()) {
            throw new IllegalStateException("May only get MOTD if not yet connected!");
        }
        bot.connect();
        //Send handshake with request to change state to STATUS
        getNetClient().writePacket(new S00Handshake(bot.getBuilder().getHost(), bot.getBuilder().getPort(),
                getProtocolID(), State.STATUS));
        //Handle internal state
        setCurrentState(State.STATUS);
        //write request
        getNetClient().writePacket(new S00Request());
        PacketLengthHeader respHead = getNetClient().readHeader();//since we're only expecting a confirmation we can skip using an actual packets

        String response = readString(getConnection().getDataInputStream());
        ObjectMapper objectMapper = new ObjectMapper();
        disconnect();
        return objectMapper.readValue(response, MOTDResponse.class);
    }

    /**
     * Packet handle method.
     * @param packet the read packet from stream.
     */
    @Override
    public void handlePacket(ReadablePacket packet) {
        if (listenerMap.containsKey(packet.getClass())) {
            listenerMap.get(packet.getClass()).forEach(listener -> listener.onPacket(bot, packet));
        }
    }

    /**
     * Register a listener for when a specified packet is read.
     * @param clazz the class of the packet we want to listen for.
     * @param listener the listener to run when we come across the specified packet.
     */
    @Override
    public void registerPacketListener(Class<? extends ReadablePacket> clazz, PacketListener listener) {
        if (!listenerMap.containsKey(clazz)) {
            listenerMap.put(clazz, new ArrayList<>());
        }

        listenerMap.get(clazz).add(listener);
    }

    /**
     * Unregister a listener from a packet.
     * @param clazz the class of the packet we want to unregister.
     * @param listener the listener that was listening to the specified packet.
     */
    @Override
    public void unregisterPacketListener(Class<? extends ReadablePacket> clazz, PacketListener listener) {
        if (!listenerMap.containsKey(clazz)) {
            return;
        }

        listenerMap.get(clazz).remove(listener);
        if (listenerMap.get(clazz).isEmpty()) {
            listenerMap.remove(clazz);
        }
    }

    /**
     * Handle a tick from the tick engine.
     */
    @Override
    public void tick() {
    }

    /**
     * Handle an incoming chat message
     * @param component the chat component that was received.
     * @param position chat components aren't only for the chat box.
     *                 0=chatbox, 1=system message, 2=above hotbar
     */
    @Override
    public void onChatMessage(ChatComponent component, byte position) {
        if (position == 1) {
            logger.info("CHAT: {}", component.getCompleteText());
        }
    }

    /**
     * Disconnect from the server.
     */
    @Override
    public void disconnect() throws IOException {
        //TODO: implement correct disconnect
        getTickEngine().stop();
        getNetClient().close();
    }

    /**
     * @return instance of our tick engine.
     */
    @Override
    public TickEngine getTickEngine() {
        return tickEngine;
    }

    /**
     * Send a chat message to the server.
     *
     * @param msg the message contents. No json.
     */
    @Override
    public void sendChatMessage(String msg) throws IOException {
        getNetClient().sendPacket(new S02ChatMessage(msg));
    }

    /**
     * Enable compression on the way packets are communicated both ways.
     * @param threshold min packet size before compression is done to that packet.
     * @see DefaultNetClient for an example
     */
    private void enableCompression(int threshold) {
        bot.getNetClient().enableCompression(threshold);
    }

    /**
     * Sets up the cryptography requirements sent by the server.
     * @param cryptoRequest the packet from the server that requests crypto
     * @throws IOException when something goes wrong setting up crypto
     */
    private void setupCrypto(C01EncryptionRequest cryptoRequest) throws IOException {
        if(!(getConnection() instanceof KeySecuredConnection)) {
            throw new ProtocolException("Protocol " + getProtocolID() + " only accepts connections of type " + KeySecuredConnection.class.getName() + "\r\n" +
                                        "But connection is of type " + getConnection().getClass().getName());
        }
        KeySecuredConnection secureConnection = (KeySecuredConnection) getConnection();
        logger.info("Enabling crypto");
        try {
            PublicKey pubKey = EncryptionUtil.generatePublicKey(cryptoRequest.getPubKey());
            SecretKey secKey = EncryptionUtil.generateSecretKey();
            String hash = new BigInteger(EncryptionUtil.encrypt(cryptoRequest.getServerId(), pubKey, secKey)).toString(16);
            bot.getSessionProvider().authenticateServer(bot.getSession(), hash);
            getNetClient().writePacket(new S01EncryptionResponse(pubKey, secKey, cryptoRequest.getVerifyToken()));
            secureConnection.setSharedKey(secKey);
            secureConnection.secure();
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
        }
    }

    /**
     * Easier access
     * @return the NetClient used by the bot
     */
    private NetClient getNetClient() {
        return bot.getNetClient();
    }

    /**
     * Easier access
     * @return the Connection being used by the bot.
     */
    private Connection getConnection() {
        return bot.getConnection();
    }
}
